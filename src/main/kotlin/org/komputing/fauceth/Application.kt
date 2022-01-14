package org.komputing.fauceth

import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    if (config.chains.size != chains.size) fail("Could not find definitions for all chains")
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        configureRouting()
    }.start(wait = true)
}