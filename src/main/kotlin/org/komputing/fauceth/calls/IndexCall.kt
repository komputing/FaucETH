package org.komputing.fauceth.calls

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.response.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import org.komputing.fauceth.*
import org.komputing.fauceth.util.getTitle
import org.komputing.fauceth.util.log

internal suspend fun PipelineContext<Unit, ApplicationCall>.indexCall() {
    log(FaucethLogLevel.VERBOSE, "Serving /")
    if (chains.isEmpty()) {
        call.respondText("No chain configured")
        log(FaucethLogLevel.ERROR, "no chain configured")
        return
    }
    val address = call.request.queryParameters[ADDRESS_KEY]
    val callback = call.request.queryParameters[CALLBACK_KEY]
    val requestedChain = call.request.queryParameters[CHAIN_KEY]?.toLongOrNull().let { chainId ->
        chains.firstOrNull { it.staticChainInfo.chainId == chainId }
    }
    val title = getTitle(requestedChain)
    call.respondHtml {
        head {
            title { +title }

            script(src = "https://js.hcaptcha.com/1/api.js") {}

            script(src = "/static/js/main.js") {}

            meta(name = "keywords") {
                content = keywords
            }
            styleLink("/static/css/main.css")
            styleLink("/static/css/gh-fork-ribbon.css")

            styleLink("/static/css/sweetalert2.min.css")
            script(src = "/static/js/sweetalert2.all.min.js") {}

            link(rel = "manifest", href = "/static/site.webmanifest")
            link(rel = "apple-touch-icon", href = "/static/favicon/apple-touch-icon.png")
            link(rel = "icon", href = "/static/favicon/favicon-32x32.png") {
                attributes["sizes"] = "32x32"
            }
            link(rel = "icon", href = "/static/favicon/favicon-16x16.png") {
                attributes["sizes"] = "16x16"
            }
        }
        body {
            a(href = "https://github.com/komputing/FaucETH", classes = "github-fork-ribbon fixed") {
                attributes["title"] = "Fork me on GitHub"
                attributes["data-ribbon"] = "Fork me on GitHub"
                +"Fork me on Fork me on GitHub"
            }
            div(classes = "container") {
                div {
                    form {
                        id = "mainForm"

                        h1(classes = "center") {
                            +title
                        }
                        config.appHeroImage?.let { url ->
                            div(classes = "center") {
                                img(src = url, classes = "image")
                            }
                        }
                        callback?.let { notNullCallback ->
                            hiddenInput {
                                name = "callback"
                                value = notNullCallback
                            }
                        }
                        if (chains.size > 1 && requestedChain == null) {
                            select {
                                name = "chain"
                                chains.forEach { chain ->
                                    option {
                                        value = chain.staticChainInfo.chainId.toString()
                                        +chain.staticChainInfo.name
                                    }
                                }
                            }
                        } else {
                            hiddenInput {
                                name = "chain"
                                value = (requestedChain ?: chains.first()).staticChainInfo.chainId.toString()
                            }
                        }

                        br
                        input(classes = "input") {
                            name = ADDRESS_KEY
                            value = address ?: ""
                            placeholder = "Please enter some address or ENS name"
                        }
                        config.hcaptchaSiteKey?.let { hcaptchaSiteKey ->
                            div(classes = "h-captcha center") {
                                attributes["data-sitekey"] = hcaptchaSiteKey
                            }
                        }
                    }
                    div(classes = "center") {
                        button(classes = "button") {
                            onClick = "submitForm()"
                            +"Request funds"
                        }
                    }
                }
            } // container
            if (config.footerHTML != null) {
                footer(classes = "site-footer") {
                    p {
                        unsafe {
                            +config.footerHTML
                        }
                    }
                }
            }
        }
    }
}