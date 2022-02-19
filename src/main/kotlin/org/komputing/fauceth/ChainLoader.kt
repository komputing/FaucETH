package org.komputing.fauceth

import kotlinx.coroutines.*
import org.kethereum.crypto.toAddress
import org.kethereum.rpc.*
import org.komputing.fauceth.util.AtomicNonce
import org.komputing.fauceth.util.log
import java.math.BigInteger

fun CoroutineScope.loadChains() {
    unfilteredChains.filter { config.chains.contains(BigInteger.valueOf(it.chainId)) }.forEach {
        launch(Dispatchers.IO) {
            val rpcURL = it.rpc.first().replace("\${INFURA_API_KEY}", config.infuraProject ?: "none")

            val rpc = if (config.logging == FaucethLogLevel.VERBOSE) {
                BaseEthereumRPC(ConsoleLoggingTransportWrapper(HttpTransport(rpcURL)))
            } else {
                HttpEthereumRPC(rpcURL)
            }

            var initialNonce: BigInteger? = null

            while (initialNonce == null) {
                log(FaucethLogLevel.INFO, "Fetching initial nonce for chain ${it.name}")
                try {
                    initialNonce = rpc.getTransactionCount(config.keyPair.toAddress())
                } catch (e: EthereumRPCException) {
                    log(FaucethLogLevel.ERROR, "could not get initial nonce due to " + e.message)
                }
                delay(1000)
            }

            log(FaucethLogLevel.INFO, "Got initial nonce for chain ${it.name}: $initialNonce for address ${config.keyPair.toAddress()}")

            chains.add(
                ExtendedChainInfo(
                    it,
                    confirmedNonce = AtomicNonce(initialNonce.minus(BigInteger.ONE)),
                    pendingNonce = AtomicNonce(initialNonce),
                    rpc = rpc
                )
            )
        }
    }
}