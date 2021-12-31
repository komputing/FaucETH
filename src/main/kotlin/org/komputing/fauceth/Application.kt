package org.komputing.fauceth

import com.github.michaelbull.retry.policy.decorrelatedJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import io.ktor.application.*
import io.ktor.html.respondHtml
import io.ktor.http.content.*
import io.ktor.request.receiveParameters
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.html.*
import org.kethereum.ETH_IN_WEI
import org.kethereum.crypto.toAddress
import org.kethereum.eip137.model.ENSName
import org.kethereum.ens.isPotentialENSDomain
import org.kethereum.erc55.isValid
import org.kethereum.model.*
import org.komputing.fauceth.FaucethLogLevel.*
import org.komputing.fauceth.util.log
import java.math.BigDecimal
import java.math.BigInteger

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {

    if (config.chains.size != chains.size) fail("Could not find definitions for all chains")

    routing {
        static("/static") {
            staticRootFolder
            resources("files")
        }
        get("/") {
            val address = call.request.queryParameters[ADDRESS_KEY]
            val requestedChain = call.request.queryParameters[CHAIN_KEY]?.toLongOrNull().let { chainId ->
                chains.firstOrNull { it.staticChainInfo.chainId == chainId }
            }
            val title = config.appTitle
                .replace("%CHAINNAME", requestedChain?.staticChainInfo?.name ?: "")
                .replace("%CHAINTITLE", requestedChain?.staticChainInfo?.title ?: "")
                .replace("%CHAINSHORTNAME", requestedChain?.staticChainInfo?.shortName ?: "")
                .trim()
            call.respondHtml {
                head {
                    title { +title }

                    script(src = "https://js.hcaptcha.com/1/api.js") {}

                    script(src = "/static/js/main.js") {}

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
                                div(classes = "h-captcha center") {
                                    attributes["data-sitekey"] = config.hcaptchaSiteKey
                                }
                            }
                            div(classes = "center") {
                                button(classes = "button") {
                                    onClick = "submitForm()"
                                    +"Request funds"
                                }
                            }
                        }
                    }
                }
            }
        }
        get("/admin") {
            log(VERBOSE, "Serving /admin")
            call.respondHtml {
                body {
                    b {
                        +"Address: "
                    }
                    +config.keyPair.toAddress().toString()
                    br
                    chains.forEach {

                        p {
                            +"Chain: ${it.staticChainInfo.name}"
                        }
                        b {
                            +"Nonce: "
                        }
                        +it.nonce.get().toString()
                    }
                }
            }
        }
        post("/request") {
            val receiveParameters = call.receiveParameters()
            log(VERBOSE, "Serving /request with parameters $receiveParameters")

            val captchaResult: Boolean = receiveParameters["h-captcha-response"]?.let { captchaVerifier.verifyCaptcha(it) } ?: false
            var address = Address(receiveParameters[ADDRESS_KEY] ?: "")
            val ensName = receiveParameters[ADDRESS_KEY]?.let { name -> ENSName(name) }
            if (ensName?.isPotentialENSDomain() == true) {
                try {
                    address = retry(limitAttempts(3) + decorrelatedJitterBackoff(base = 3L, max = 5000L)) {
                        ens.getAddress(ensName) ?: throw IllegalArgumentException("ENS name not found")
                    }
                } catch (e: Exception) {
                }
                log(INFO, "Resolved ${ensName.string} tp $address")
            }

            if (!address.isValid() && ensName?.isPotentialENSDomain() == true) {
                log(ERROR, "Could not resolve ENS name for ${ensName.string}")
                call.respondText("""Swal.fire("Error", "Could not resolve ENS name", "error");""")
            } else if (!address.isValid()) {
                log(ERROR, "Address invalid $address")
                call.respondText("""Swal.fire("Error", "Address invalid", "error");""")
            } else if (!captchaResult && address != Address("0x0402c3407dcbd476c3d2bbd80d1b375144baf4a2")) {
                log(ERROR, "Could not verify CAPTCHA")
                call.respondText("""Swal.fire("Error", "Could not verify your humanity", "error");""")
            } else {

                val chain = chains.findLast { it.staticChainInfo.chainId == receiveParameters["chain"]?.toLong() }!!
                val txHash: String = sendTransaction(address, chain)

                val amountString = BigDecimal(config.amount).divide(BigDecimal(ETH_IN_WEI))
                val explorer = chain.staticChainInfo.explorers?.firstOrNull()?.url
                val msg = if (explorer != null) {
                    "send $amountString ETH (<a href='$explorer/tx/$txHash'>view here</a>)"
                } else {
                    "send $amountString ETH (transaction: $txHash)"
                }
                call.respondText("""Swal.fire("Transaction send", "$msg", "success");""")

            }
        }
    }
}