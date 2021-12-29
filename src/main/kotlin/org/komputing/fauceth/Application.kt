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
import kotlinx.html.*
import org.kethereum.DEFAULT_GAS_LIMIT
import org.kethereum.ETH_IN_WEI
import org.kethereum.crypto.toAddress
import org.kethereum.eip137.model.ENSName
import org.kethereum.eip1559.signer.signViaEIP1559
import org.kethereum.eip1559_fee_oracle.suggestEIP1559Fees
import org.kethereum.ens.isPotentialENSDomain
import org.kethereum.erc55.isValid
import org.kethereum.extensions.transactions.encode
import org.kethereum.model.*
import org.komputing.fauceth.FaucethLogLevel.*
import org.komputing.fauceth.util.AtomicNonce
import org.komputing.fauceth.util.log
import org.walleth.khex.toHexString
import java.lang.IllegalStateException
import java.math.BigDecimal

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {

    val initialNonce = rpc.getTransactionCount(config.keyPair.toAddress())

    log(INFO, "Got initial nonce: $initialNonce for address ${config.keyPair.toAddress()}")

    val atomicNonce = AtomicNonce(initialNonce!!)

    routing {
        static("/static") {
            staticRootFolder
            resources("files")
        }
        get("/") {
            val address = call.request.queryParameters[ADDRESS_KEY]

            call.respondHtml {
                head {
                    title { +config.appTitle }

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
                                    +config.appTitle
                                }
                                config.appHeroImage?.let { url ->
                                    div(classes = "center") {
                                        img(src = url, classes = "image")
                                    }
                                }
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
                    b {
                        +"Nonce: "
                    }
                    +atomicNonce.get().toString()
                }

            }
        }
        post("/request") {
            val receiveParameters = call.receiveParameters()
            log(VERBOSE, "Serving /request with parameters $receiveParameters")

            val captchaResult: Boolean = verifyCaptcha(receiveParameters["h-captcha-response"] ?: "", config.hcaptchaSecret)
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
            } else if (!captchaResult) {
                log(ERROR, "Could not verify CAPTCHA")
                call.respondText("""Swal.fire("Error", "Could not verify your humanity", "error");""")
            } else {

                val tx = createEmptyTransaction().apply {
                    to = address
                    value = config.amount
                    nonce = atomicNonce.getAndIncrement()
                    gasLimit = DEFAULT_GAS_LIMIT
                    chain = config.chainId
                }

                val feeSuggestionResult = retry(decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
                    val feeSuggestionResults = suggestEIP1559Fees(rpc)
                    log(VERBOSE, "Got FeeSuggestionResults $feeSuggestionResults")
                    (feeSuggestionResults.keys.minOrNull() ?: throw IllegalArgumentException("Could not get 1559 fees")).let {
                        feeSuggestionResults[it]
                    }
                }

                tx.maxPriorityFeePerGas = feeSuggestionResult!!.maxPriorityFeePerGas
                tx.maxFeePerGas = feeSuggestionResult.maxFeePerGas

                log(INFO, "Signing transaction $tx")
                val signature = tx.signViaEIP1559(config.keyPair)

                val txHash: String = retry(decorrelatedJitterBackoff(base = 10L, max = 5000L)) {
                    val res = rpc.sendRawTransaction(tx.encode(signature).toHexString())

                    if (res?.startsWith("0x") != true) {
                        log(ERROR, "sendRawTransaction got no hash $res")
                        throw IllegalStateException("Got no hash from RPC for tx")
                    }
                    res
                }

                val amountString = BigDecimal(config.amount).divide(BigDecimal(ETH_IN_WEI))
                val msg = if (config.chainExplorer != null) {
                    "send $amountString ETH (<a href='${config.chainExplorer}/tx/$txHash'>view here</a>)"
                } else {
                    "send $amountString ETH (transaction: $txHash)"
                }
                call.respondText("""Swal.fire("Transaction send", "$msg", "success");""")
            }
        }
    }

}