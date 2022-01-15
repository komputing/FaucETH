package org.komputing.fauceth.calls

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.util.pipeline.*
import kotlinx.html.a
import kotlinx.html.b
import kotlinx.html.body
import kotlinx.html.h2
import org.kethereum.ETH_IN_WEI
import org.kethereum.crypto.toAddress
import org.komputing.fauceth.FaucethLogLevel
import org.komputing.fauceth.chains
import org.komputing.fauceth.config
import org.komputing.fauceth.util.keyValueHTML
import org.komputing.fauceth.util.log
import org.komputing.fauceth.util.toRelativeTimeString

internal suspend fun PipelineContext<Unit, ApplicationCall>.adminCall() {
    log(FaucethLogLevel.VERBOSE, "Serving /admin")
    call.respondHtml {
        body {
            b {
                +"Address: "
            }
            +config.keyPair.toAddress().toString()
            chains.forEach { chainInfo ->
                h2 {
                    +chainInfo.staticChainInfo.name
                }
                keyValueHTML("pending Nonce") { +chainInfo.pendingNonce.get().toString() }
                keyValueHTML("confirmed Nonce") { +chainInfo.confirmedNonce.get().toString() }
                chainInfo.lastSeenBalance?.let { lastSeenBalance ->
                    keyValueHTML("Balance") {
                        +((lastSeenBalance / ETH_IN_WEI).toString() + chainInfo.staticChainInfo.nativeCurrency.symbol + " = "
                                + ((lastSeenBalance / config.amount).toString() + " servings"))
                    }
                }
                chainInfo.staticChainInfo.explorers?.firstOrNull()?.let {
                    keyValueHTML("Explorer") {
                        a {
                            href = it.url + "/address/" + config.keyPair.toAddress().toString()
                            +"link"
                        }
                    }
                }
                chainInfo.lastRequested?.let { lastRequested ->
                    keyValueHTML("Last Request") { +lastRequested.toRelativeTimeString() }

                    chainInfo.lastConfirmation?.let { lastConfirmed ->
                        keyValueHTML("Last Confirm") { +lastConfirmed.toRelativeTimeString() }
                    }

                }

                keyValueHTML("Addresses in map") { +chainInfo.addressToTimeMap.keys.size.toString() }
            }
        }
    }
}