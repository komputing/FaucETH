package org.komputing.fauceth.calls

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.util.pipeline.*
import kotlinx.html.b
import kotlinx.html.body
import kotlinx.html.h2
import org.kethereum.crypto.toAddress
import org.komputing.fauceth.FaucethLogLevel
import org.komputing.fauceth.chains
import org.komputing.fauceth.config
import org.komputing.fauceth.util.keyValueHTML
import org.komputing.fauceth.util.log

internal suspend fun PipelineContext<Unit, ApplicationCall>.adminCall() {
    log(FaucethLogLevel.VERBOSE, "Serving /admin")
    call.respondHtml {
        body {
            b {
                +"Address: "
            }
            +config.keyPair.toAddress().toString()
            chains.forEach {
                h2 {
                    +it.staticChainInfo.name
                }
                keyValueHTML("pending Nonce", it.pendingNonce.get().toString())
                keyValueHTML("confirmed Nonce", it.confirmedNonce.get().toString())
                keyValueHTML("Balance", it.lastSeenBalance.toString())
            }
        }
    }
}