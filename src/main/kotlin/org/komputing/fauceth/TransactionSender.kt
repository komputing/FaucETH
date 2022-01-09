package org.komputing.fauceth

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.StopRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.decorrelatedJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.delay
import org.kethereum.crypto.toAddress
import org.kethereum.eip155.signViaEIP155
import org.kethereum.eip1559.detector.isEIP1559
import org.kethereum.eip1559.signer.signViaEIP1559
import org.kethereum.eip1559_fee_oracle.suggestEIP1559Fees
import org.kethereum.extensions.transactions.encode
import org.kethereum.keccakshortcut.keccak
import org.kethereum.model.Address
import org.kethereum.model.ChainId
import org.kethereum.model.createEmptyTransaction
import org.kethereum.rpc.EthereumRPCException
import org.komputing.fauceth.util.log
import org.walleth.khex.toHexString
import java.io.IOException
import java.lang.IllegalStateException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.BigInteger.*

suspend fun sendTransaction(address: Address, txChain: ExtendedChainInfo): String? {

    val tx = createEmptyTransaction().apply {
        to = address
        value = config.amount
        nonce = txChain.pendingNonce.getAndIncrement()
        from = config.keyPair.toAddress()
        chain = txChain.staticChainInfo.chainId.toBigInteger()
    }

    val txHashList = mutableListOf<String>()

    try {
        while (true) {
            tx.gasLimit = retry {
                // TODO usually with most chains it is fixed at 21k - so there is room for RPC call amount optimize here
                txChain.rpc.estimateGas(tx) ?: throw EthereumRPCException("Could not estimate gas limit", 404)
            }
            if (txChain.useEIP1559) {
                val handle1559NotAvailable: RetryPolicy<Throwable> = {
                    if (reason is EthereumRPCException && (reason.message == "the method eth_feeHistory does not exist/is not available") || (reason.message == "rpc method is not whitelisted")) StopRetrying else ContinueRetrying
                }

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

                            if (tx.maxPriorityFeePerGas == null) {
                                // initial fee
                                tx.maxPriorityFeePerGas = feeSuggestionResult.maxPriorityFeePerGas
                                tx.maxFeePerGas = feeSuggestionResult.maxFeePerGas
                                log(FaucethLogLevel.INFO, "Signing Transaction $tx")
                            } else {
                                // replacement fee (e.g. there was a surge after we calculated the fee and tx is not going in this way
                                tx.maxPriorityFeePerGas = feeSuggestionResult.maxPriorityFeePerGas.max(tx.maxPriorityFeePerGas!!)
                                // either replace with new feeSuggestion or 20% more than previous to prevent replacement tx underpriced
                                tx.maxFeePerGas =
                                    feeSuggestionResult.maxFeePerGas.max(tx.maxFeePerGas!!.toBigDecimal().multiply(BigDecimal("1.2")).toBigInteger())
                                log(FaucethLogLevel.INFO, "Signing Transaction with replacement fee $tx")

                            }
                        }
                    } catch (e: EthereumRPCException) {
                        log(FaucethLogLevel.INFO, "Chain does not seem to support 1559")
                        txChain.useEIP1559 = false
                    }
                }
            }

            if (!txChain.useEIP1559) {
                tx.gasPrice = retry(limitAttempts(5) + decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
                    txChain.rpc.gasPrice()
                }?.max(((tx.gasPrice ?: ONE).toBigDecimal() * BigDecimal("1.2")).toBigInteger())
                log(FaucethLogLevel.INFO, "Signing Transaction $tx")
            }
            val signature = if (tx.isEIP1559()) tx.signViaEIP1559(config.keyPair) else tx.signViaEIP155(config.keyPair, ChainId(tx.chain!!))

            txChain.lastSeenBalance = retry {
                txChain.rpc.getBalance(config.keyPair.toAddress()) ?: throw IOException("Could not get balance")
            }

            if (txChain.lastSeenBalance!! < tx.value!!.multiply(TWO)) { // TODO improve threshold
                return null
            }

            val encodedTransaction = tx.encode(signature)
            val hash = encodedTransaction.keccak()
            txHashList.add(hash.toHexString())

            try {
                val noRetryWhenKnown: RetryPolicy<Throwable> = {
                    if (reason is EthereumRPCException && reason.message == "already known") StopRetrying else ContinueRetrying
                }

                 retry(limitAttempts(5) + noRetryWhenKnown + decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
                    val res = txChain.rpc.sendRawTransaction(encodedTransaction.toHexString())

                    if (res?.startsWith("0x") != true) {
                        log(FaucethLogLevel.ERROR, "sendRawTransaction got no hash $res")
                        throw EthereumRPCException("Got no hash from RPC for tx", 404)
                    }

                }

            } catch (e: EthereumRPCException) {
                // might be "Replacement tx too low", "already known" or "nonce too low" when previous tx was accepted
            }

            var txBlockNumber: BigInteger?

            if (tx.nonce == txChain.confirmedNonce.get().plus(ONE)) {
                repeat(20) { // after 20 attempts we will try with a new fee calculation
                    txHashList.forEach { hash ->
                        // we wait for *any* tx we signed in this context to confirm - there could be (edge)cases where a old tx confirms and so a replacement tx will not
                        txBlockNumber = txChain.rpc.getTransactionByHash(hash)?.transaction?.blockNumber
                        if (txBlockNumber != null) {
                            tx.nonce?.let { txChain.confirmedNonce.setPotentialNewMax(it) }
                            return hash
                        }
                        delay(100)
                    }
                    delay(700)
                }
            }
        }
    } catch (rpce: EthereumRPCException) {
        return null
    }
}