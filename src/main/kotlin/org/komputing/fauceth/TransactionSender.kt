package org.komputing.fauceth

import com.github.michaelbull.retry.policy.decorrelatedJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.delay
import org.kethereum.eip155.signViaEIP155
import org.kethereum.eip1559.detector.isEIP1559
import org.kethereum.eip1559.signer.signViaEIP1559
import org.kethereum.eip1559_fee_oracle.suggestEIP1559Fees
import org.kethereum.extensions.transactions.encode
import org.kethereum.keccakshortcut.keccak
import org.kethereum.model.Address
import org.kethereum.model.ChainId
import org.kethereum.model.Transaction
import org.kethereum.model.createEmptyTransaction
import org.kethereum.rpc.EthereumRPCException
import org.komputing.fauceth.util.handle1559NotAvailable
import org.komputing.fauceth.util.isUnRecoverableEIP1559Error
import org.komputing.fauceth.util.log
import org.komputing.fauceth.util.noRetryWhenNotRecoverable
import org.walleth.khex.toHexString
import java.math.BigInteger
import java.math.BigInteger.*

sealed interface SendTransactionResult

data class SendTransactionOk(val hash: String?) : SendTransactionResult
data class SendTransactionError(val message: String) : SendTransactionResult

suspend fun sendTransaction(address: Address, txChain: ExtendedChainInfo): SendTransactionResult {

    val txNonce = txChain.pendingNonce.getAndIncrement()
    var tx = createEmptyTransaction().apply {
        to = address
        value = config.amount
        nonce = txNonce
        from = config.address
        chain = txChain.staticChainInfo.chainId.toBigInteger()
    }

    val metaData = AddressInfo(System.currentTimeMillis())
    txChain.addressMeta[address] = metaData

    while (true) {
        try {

            val deltaToConfirmed = txNonce - txChain.confirmedNonce.get()

            tx = tx.copy()
            if (deltaToConfirmed < BigInteger("7")) {
                tryCreateAndSendTx(tx, txChain, metaData)?.let {
                    return it
                }
            }

            if (deltaToConfirmed == ONE) {
                tryConfirmTransaction(metaData, txChain)?.let {
                    return it
                }
            }

        } catch (rpce: EthereumRPCException) {
            if (rpce.message.contains("insufficient funds")) {
                return SendTransactionError(rpce.message)
            }
        }
        delay(1000)
    }

}

private suspend fun tryCreateAndSendTx(
    tx: Transaction,
    txChain: ExtendedChainInfo,
    meta: AddressInfo
): SendTransactionError? {
    if (tx.gasLimit != BigInteger("21000")) { // most chains are fixed at 21k - only estimate once here
        tx.gasLimit = retry {
            txChain.rpc.estimateGas(tx) ?: throw EthereumRPCException("Could not estimate gas limit", 404)
        }
    }
    val hasNoFeeYet = tx.maxPriorityFeePerGas == null && tx.gasPrice == null
    if (hasNoFeeYet || (System.currentTimeMillis() - (tx.creationEpochSecond ?: System.currentTimeMillis())) > 20000) {
        if (txChain.useEIP1559) {

            try {
                retry(handle1559NotAvailable + decorrelatedJitterBackoff(base = 10L, max = 5000L)) {

                    val feeSuggestionResults = suggestEIP1559Fees(txChain.rpc)
                    log(FaucethLogLevel.VERBOSE, "Got FeeSuggestionResults $feeSuggestionResults")
                    (feeSuggestionResults.keys.minOrNull() ?: throw EthereumRPCException("Could not get 1559 fees", 404)).let {
                        feeSuggestionResults[it]
                    }

                }?.let { feeSuggestionResult ->

                    if (feeSuggestionResult.maxFeePerGas > (tx.maxFeePerGas ?: ZERO)) {
                        tx.maxPriorityFeePerGas = feeSuggestionResult.maxPriorityFeePerGas
                        tx.maxFeePerGas = feeSuggestionResult.maxFeePerGas
                    }
                }
            } catch (e: EthereumRPCException) {
                if (e.isUnRecoverableEIP1559Error()) {
                    log(FaucethLogLevel.INFO, "Chain does not seem to support 1559")
                    txChain.useEIP1559 = false
                }
            }
        }

        if (!txChain.useEIP1559) {
            tx.gasPrice = retry(limitAttempts(5) + decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
                txChain.rpc.gasPrice()
            }?.max(tx.gasPrice ?: ONE)
            log(FaucethLogLevel.INFO, "Signing Transaction $tx")
        }
        val signature = if (tx.isEIP1559()) tx.signViaEIP1559(config.keyPair) else tx.signViaEIP155(config.keyPair, ChainId(tx.chain!!))

        txChain.lastSeenBalance = retry {
            txChain.rpc.getBalance(config.address) ?: throw EthereumRPCException("Could not get balance", 404)
        }

        if (txChain.lastSeenBalance!! < tx.value!!.shl(1)) { // TODO improve threshold
            return SendTransactionError("Faucet is dry")
        }

        tx.creationEpochSecond = System.currentTimeMillis()

        val encodedTransaction = tx.encode(signature)
        val hash = encodedTransaction.keccak()

        tx.txHash = hash.toHexString()

        retry(limitAttempts(5) + noRetryWhenNotRecoverable + decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
            val res = txChain.rpc.sendRawTransaction(encodedTransaction.toHexString())

            if (res?.startsWith("0x") != true) {
                log(FaucethLogLevel.ERROR, "sendRawTransaction got no hash $res")
                throw EthereumRPCException("Got no hash from RPC for tx", 404)
            }

            meta.pendingTxList.add(tx)

        }
    }
    return null
}

private suspend fun tryConfirmTransaction(
    meta: AddressInfo,
    txChain: ExtendedChainInfo
): SendTransactionOk? {
    repeat(20) { // after 20 attempts we will try with a new fee calculation

        val isTransactionConfirmed = (txChain.rpc.getTransactionCount(config.address) ?: ZERO) > (txChain.confirmedNonce.get() + ONE)
        if (isTransactionConfirmed) {
            txChain.confirmedNonce.setPotentialNewMax(txChain.confirmedNonce.get() + ONE)
            txChain.lastConfirmation = System.currentTimeMillis()

              repeat(20) {
                meta.pendingTxList.forEach { tx ->
                    // we wait for *any* tx we signed in this context to confirm - there could be (edge)cases where a old tx confirms and so a replacement tx will not
                    tx.txHash?.let { txHash ->
                        val txBlockNumber = txChain.rpc.getTransactionByHash(txHash)?.transaction?.blockNumber
                        if (txBlockNumber != null) {

                            meta.pendingTxList.clear()
                            meta.confirmedTx = tx
                            return SendTransactionOk(txHash)
                        }
                    }
                }
                delay(400)
            }

            return SendTransactionOk(null)
        }
        delay(700)
    }
    return null
}