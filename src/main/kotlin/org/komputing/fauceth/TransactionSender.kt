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
import java.io.IOException
import java.math.BigInteger
import java.math.BigInteger.*

sealed interface SendTransactionResult

data class SendTransactionOk(val hash: String) : SendTransactionResult
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

    try {
        while (true) {
            val deltaToConfirmed = txNonce - txChain.confirmedNonce.get()

            tx = tx.copy()
            if (deltaToConfirmed < BigInteger("7")) {
                tryCreateAndSendTx(tx, txChain, metaData)?.let {
                    return it
                }
            }

            if (deltaToConfirmed == ONE) {
                tryConfirmTransaction(metaData, txChain)?.let {
                    return SendTransactionOk(it)
                }
            }

            delay(100)
        }
    } catch (rpce: EthereumRPCException) {
        return SendTransactionError(rpce.message)
    }
}

private suspend fun tryCreateAndSendTx(
    tx: Transaction,
    txChain: ExtendedChainInfo,
    meta: AddressInfo
): SendTransactionError? {
    tx.gasLimit = retry {
        // TODO usually with most chains it is fixed at 21k - so there is room for RPC call amount optimize here
        txChain.rpc.estimateGas(tx) ?: throw EthereumRPCException("Could not estimate gas limit", 404)
    }
    if (txChain.useEIP1559) {

        if (tx.maxPriorityFeePerGas == null || System.currentTimeMillis() - (tx.creationEpochSecond ?: System.currentTimeMillis()) > 20000) {
            try {
                retry(handle1559NotAvailable + decorrelatedJitterBackoff(base = 10L, max = 5000L)) {

                    val feeSuggestionResults = suggestEIP1559Fees(txChain.rpc)
                    log(FaucethLogLevel.VERBOSE, "Got FeeSuggestionResults $feeSuggestionResults")
                    (feeSuggestionResults.keys.minOrNull() ?: throw IllegalArgumentException("Could not get 1559 fees")).let {
                        feeSuggestionResults[it]
                    }

                }?.let { feeSuggestionResult ->
                    tx.creationEpochSecond = System.currentTimeMillis()

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
    }

    if (!txChain.useEIP1559) {
        tx.gasPrice = retry(limitAttempts(5) + decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
            txChain.rpc.gasPrice()
        }?.max(tx.gasPrice ?: ONE)
        log(FaucethLogLevel.INFO, "Signing Transaction $tx")
    }
    val signature = if (tx.isEIP1559()) tx.signViaEIP1559(config.keyPair) else tx.signViaEIP155(config.keyPair, ChainId(tx.chain!!))

    txChain.lastSeenBalance = retry {
        txChain.rpc.getBalance(config.address) ?: throw IOException("Could not get balance")
    }

    if (txChain.lastSeenBalance!! < tx.value!!.shl(1)) { // TODO improve threshold
        return SendTransactionError("Faucet is dry")
    }

    val encodedTransaction = tx.encode(signature)
    val hash = encodedTransaction.keccak()

    tx.txHash = hash.toHexString()

    try {

        retry(limitAttempts(5) + noRetryWhenNotRecoverable + decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
            val res = txChain.rpc.sendRawTransaction(encodedTransaction.toHexString())

            if (res?.startsWith("0x") != true) {
                log(FaucethLogLevel.ERROR, "sendRawTransaction got no hash $res")
                throw EthereumRPCException("Got no hash from RPC for tx", 404)
            }

            meta.pendingTxList.add(tx)

        }

    } catch (e: EthereumRPCException) {
        // might be "Replacement tx too low", "already known" or "nonce too low" when previous tx was accepted
    }
    return null
}

private suspend fun tryConfirmTransaction(
    meta: AddressInfo,
    txChain: ExtendedChainInfo
): String? {
    repeat(20) { // after 20 attempts we will try with a new fee calculation
        meta.pendingTxList.forEach { tx ->
            // we wait for *any* tx we signed in this context to confirm - there could be (edge)cases where a old tx confirms and so a replacement tx will not
            tx.txHash?.let { txHash ->
                val txBlockNumber = txChain.rpc.getTransactionByHash(txHash)?.transaction?.blockNumber
                if (txBlockNumber != null) {
                    tx.nonce?.let { txChain.confirmedNonce.setPotentialNewMax(it) }
                    txChain.lastConfirmation = System.currentTimeMillis()
                    meta.pendingTxList.clear()
                    meta.confirmedTx = tx
                    return txHash
                }
            }
            delay(100)
        }
        delay(700)
    }
    return null
}