package org.komputing.fauceth.calls

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import org.kethereum.ens.isPotentialENSDomain
import org.komputing.fauceth.*
import org.komputing.fauceth.util.getTitle
import org.komputing.fauceth.util.log

internal suspend fun PipelineContext<Unit, ApplicationCall>.indexCall() {
    log(FaucethLogLevel.VERBOSE, "Serving /")
    if (chains.isEmpty()) {
        val message = if (config.chains.isEmpty()) "No chain configured" else "loading chains"
        call.respondText(message)
        log(FaucethLogLevel.ERROR, message)
        return
    }

    val params = ReceiveParametersProcessor(call.request.queryParameters)

    val callback = call.request.queryParameters[CALLBACK_KEY]
    val requestedChain = params.chain ?: if (chains.size == 1) chains.first() else null
    val title = getTitle(requestedChain)
    val multipleChainsPossible = chains.size > 1 && requestedChain == null

    val allDisabled = chains.all { it.lowBalance() } && !params.ensName.isPotentialENSDomain()

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
                        if (allDisabled) {
                            p(classes = "warn") {
                                +(config.lowBalanceText)
                            }
                        }
                        if (multipleChainsPossible && params.addressString.isNotEmpty()) {

                            if (!allDisabled) {
                                select {
                                    name = "chain"

                                    chains.forEach { chain ->
                                        val disable = chain.lowBalance() && !params.ensName.isPotentialENSDomain()
                                        option {
                                            value = chain.staticChainInfo.chainId.toString()
                                            disabled = disable
                                            +(chain.staticChainInfo.name + if (disable) " - only for ENS domains" else "")
                                        }
                                    }
                                }
                            }
                        } else {
                            requestedChain?.let {
                                hiddenInput {
                                    name = CHAIN_KEY
                                    value = it.staticChainInfo.chainId.toString()
                                }
                            }
                        }

                        br

                        if (params.addressString.isEmpty()) {
                            input(classes = "input") {
                                name = ADDRESS_KEY
                                value = ""
                                placeholder = if (allDisabled) "Please enter some ENS name" else "Please enter address or ENS name"
                            }
                        } else {
                            hiddenInput {
                                name = ADDRESS_KEY
                                value = params.addressString
                            }
                        }
                        if (!multipleChainsPossible || params.addressString.isNotEmpty() && !allDisabled) {
                            config.hcaptchaSiteKey?.let { hcaptchaSiteKey ->
                                div(classes = "h-captcha center") {
                                    attributes["data-sitekey"] = hcaptchaSiteKey
                                }
                            }
                        }
                    }
                    if (!allDisabled || params.addressString.isEmpty()) {
                        div(classes = "center") {
                            button(classes = "button") {
                                onClick = if (!multipleChainsPossible || params.addressString.isNotEmpty()) "submitFinalForm()" else "submitAddressForm()"
                                +"Request funds"
                            }
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