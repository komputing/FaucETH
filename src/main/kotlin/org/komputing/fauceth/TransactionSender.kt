package org.komputing.fauceth

import com.github.michaelbull.retry.policy.decorrelatedJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.delay
import org.kethereum.DEFAULT_GAS_LIMIT
import org.kethereum.eip1559.signer.signViaEIP1559
import org.kethereum.eip1559_fee_oracle.suggestEIP1559Fees
import org.kethereum.extensions.transactions.encode
import org.kethereum.model.Address
import org.kethereum.model.createEmptyTransaction
import org.kethereum.rpc.EthereumRPCException
import org.komputing.fauceth.util.AtomicNonce
import org.komputing.fauceth.util.log
import org.walleth.khex.toHexString
import java.lang.IllegalStateException
import java.math.BigDecimal
import java.math.BigInteger

suspend fun sendTransaction(address: Address, txChain: ChainWithRPCAndNonce): String {

    val tx = createEmptyTransaction().apply {
        to = address
        value = config.amount
        nonce = txChain.nonce.getAndIncrement()
        gasLimit = DEFAULT_GAS_LIMIT
        chain = txChain.staticChainInfo.chainId.toBigInteger()
    }

    val txHashList = mutableListOf<String>()

    while (true) {
        val feeSuggestionResult = retry(decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
            val feeSuggestionResults = suggestEIP1559Fees(txChain.rpc)
            log(FaucethLogLevel.VERBOSE, "Got FeeSuggestionResults $feeSuggestionResults")
            (feeSuggestionResults.keys.minOrNull() ?: throw IllegalArgumentException("Could not get 1559 fees")).let {
                feeSuggestionResults[it]
            }
        }

        if (tx.maxPriorityFeePerGas == null) {
            // initial fee
            tx.maxPriorityFeePerGas = feeSuggestionResult!!.maxPriorityFeePerGas
            tx.maxFeePerGas = feeSuggestionResult.maxFeePerGas
            log(FaucethLogLevel.INFO, "Signing Transaction $tx")
        } else {
            // replacement fee (e.g. there was a surge after we calculated the fee and tx is not going in this way
            tx.maxPriorityFeePerGas = feeSuggestionResult!!.maxPriorityFeePerGas.max(tx.maxPriorityFeePerGas!!)
            // either replace with new feeSuggestion or 20% more than previous to prevent replacement tx underpriced
            tx.maxFeePerGas = feeSuggestionResult.maxFeePerGas.max(tx.maxFeePerGas!!.toBigDecimal().multiply(BigDecimal("1.2")).toBigInteger())
            log(FaucethLogLevel.INFO, "Signing Transaction with replacement fee $tx")
        }

        val signature = tx.signViaEIP1559(config.keyPair)

        try {
            val txHash: String = retry(limitAttempts(5) + decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
                val res = txChain.rpc.sendRawTransaction(tx.encode(signature).toHexString())

                if (res?.startsWith("0x") != true) {
                    log(FaucethLogLevel.ERROR, "sendRawTransaction got no hash $res")
                    throw EthereumRPCException("Got no hash from RPC for tx", 404)
                }
                res
            }

            txHashList.add(txHash)

        } catch (e: EthereumRPCException) {
            // might be "Replacement tx too low", "already known" or "nonce too low" when previous tx was accepted
        }

        var txBlockNumber: BigInteger?

        repeat(20) { // after 20 attempts we will try with a new fee calculation
            txHashList.forEach { hash ->
                // we wait for *any* tx we signed in this context to confirm - there could be (edge)cases where a old tx confirms and so a replacement tx will not
                txBlockNumber = txChain.rpc.getTransactionByHash(hash)?.transaction?.blockNumber
                if (txBlockNumber != null) {
                    return hash
                }
                delay(100)
            }
            delay(700)
        }
    }

}