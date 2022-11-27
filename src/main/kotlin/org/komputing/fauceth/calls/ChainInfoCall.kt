package org.komputing.fauceth.calls

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import org.ethereum.lists.chains.model.Chain
import org.kethereum.ETH_IN_WEI
import org.kethereum.crypto.toAddress
import org.komputing.fauceth.*
import org.komputing.fauceth.util.getTitle
import org.komputing.fauceth.util.keyValueHTML
import org.komputing.fauceth.util.log
import org.komputing.fauceth.util.toRelativeTimeString

internal suspend fun PipelineContext<Unit, ApplicationCall>.chainInfoCall() {
    log(FaucethLogLevel.VERBOSE, "Serving /chainInfo")
    val chain = call.parameters["chain"]
    val chainInfo = unfilteredChains.firstOrNull { it.chainId == chain?.toLong() }
    call.respondHtml {
        head {
            title { +"${getTitle()} chainInfo" }

            styleLink("/static/css/status.css")
        }
        body(classes = "chainflex") {
            if (chainInfo == null) {
                h2 {
                    +"Chain not found"
                }
            } else {
                h2 {
                    +chainInfo.name
                }
                +"$chainInfo"
            }
        }
    }
}