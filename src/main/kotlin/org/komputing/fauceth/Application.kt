package org.komputing.fauceth

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*

fun main() {
    if (config.chains.size != chains.size) fail("Could not find definitions for all chains")

    startAddressCleanupRoutine()

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        configureRouting()
    }.start(wait = true)
}

private fun startAddressCleanupRoutine() {
    CoroutineScope(Dispatchers.Default).launch {
        while (true) {
            val timeout = 2 * 60 * 60 * 1000
            chains.forEach {
                it.addressMeta.forEach { pair ->
                    if (System.currentTimeMillis() - pair.value.requestedTime > timeout) {
                        it.addressMeta.remove(pair.key)
                    }
                }
            }
            delay(timeout.toLong())
        }
    }
}