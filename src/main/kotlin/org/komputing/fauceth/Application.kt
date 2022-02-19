package org.komputing.fauceth

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*

fun main() {
    runBlocking {
        loadChains()
        startAddressCleanupRoutine()

        embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
            configureRouting()
        }.start(wait = true)
    }
}

private fun startAddressCleanupRoutine() {
    CoroutineScope(Dispatchers.Default).launch {
        while (true) {
            lastPoolClean = System.currentTimeMillis()
            val timeout = 2 * 60 * 60 * 1000
            chains.forEach { chainInfo ->
                chainInfo.addressMeta.entries.removeIf { System.currentTimeMillis() - it.value.requestedTime > timeout }
            }
            delay(timeout.toLong())
        }
    }
}