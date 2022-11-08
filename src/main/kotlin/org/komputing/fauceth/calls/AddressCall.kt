package org.komputing.fauceth.calls

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import org.komputing.fauceth.FaucethLogLevel
import org.komputing.fauceth.config
import org.komputing.fauceth.util.createQR
import org.komputing.fauceth.util.getTitle
import org.komputing.fauceth.util.log

internal suspend fun PipelineContext<Unit, ApplicationCall>.addressCall() {
    log(FaucethLogLevel.VERBOSE, "Serving /address")
    call.respondHtml {
        head {
            title { +"${getTitle()} Address" }
            styleLink("/static/css/main.css")
        }
        body {
            div(classes = "container") {
                val url = "ethereum:${config.address}"
                img {
                    src = createQR(url)
                }
                br
                a(href = url) {
                    +config.address.toString()
                }
            }


        }
    }
}