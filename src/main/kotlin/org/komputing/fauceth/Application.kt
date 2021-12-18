package org.komputing.fauceth

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import io.ktor.application.*
import io.ktor.html.respondHtml
import io.ktor.http.content.*
import io.ktor.request.receiveParameters
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import org.kethereum.DEFAULT_GAS_LIMIT
import org.kethereum.ETH_IN_WEI
import org.kethereum.crypto.createEthereumKeyPair
import org.kethereum.crypto.toAddress
import org.kethereum.crypto.toECKeyPair
import org.kethereum.eip155.signViaEIP155
import org.kethereum.extensions.transactions.encodeLegacyTxRLP
import org.kethereum.model.*
import org.kethereum.rpc.HttpEthereumRPC
import org.walleth.khex.toHexString
import java.io.File
import java.math.BigInteger

const val ADDRESS_KEY = "address"
val keystoreFile = File("fauceth_keystore.json")
val chainConfig = ChainConfig("kintsugi", BigInteger.valueOf(1337702), "https://explorer.kintsugi.themerge.dev/", "https://rpc.kintsugi.themerge.dev")

val config = systemProperties() overriding
        EnvironmentVariables() overriding
        ConfigurationProperties.fromFile(File("fauceth.properties"))


fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    lateinit var keyPair: ECKeyPair

    val hcaptchaSecret = config[Key("hcaptcha.secret", stringType)]
    val hcaptchaSiteKey = config[Key("hcaptcha.sitekey", stringType)]

    if (!keystoreFile.exists()) {
        keyPair = createEthereumKeyPair()
        keystoreFile.createNewFile()
        keystoreFile.writeText(keyPair.privateKey.key.toString())
    } else {
        keyPair = PrivateKey(keystoreFile.readText().toBigInteger()).toECKeyPair()
    }

    val rpc = HttpEthereumRPC(chainConfig.rpc)

    val initialNonce = rpc.getTransactionCount(keyPair.toAddress())

    val atomicNonce = AtomicNonce(initialNonce!!)


    routing {
        static("/static") {
            staticRootFolder
            resources("files")
        }
        get("/") {
            val address = call.request.queryParameters[ADDRESS_KEY]
            render(address, hcaptchaSiteKey)
        }
        post("/request") {
            val receiveParameters = call.receiveParameters()

            val captchaResult: Boolean = verifyCaptcha(receiveParameters["h-captcha-response"] ?: "", hcaptchaSecret)
            val address = receiveParameters[ADDRESS_KEY]
            if (address?.length != 42) {
                call.respondText("""Swal.fire("Error", "Address invalid", "error");""")
            } else if (!captchaResult) {
                call.respondText("""Swal.fire("Error", "Could not verify your humanity", "error");""")
            } else {

                val tx = createEmptyTransaction().apply {
                    to = Address(address)
                    value = ETH_IN_WEI
                    nonce = atomicNonce.getAndIncrement()
                    gasLimit = DEFAULT_GAS_LIMIT
                    gasPrice = BigInteger.valueOf(1000000000)
                    chain = chainConfig.chainId
                }

                val signature = tx.signViaEIP155(keyPair, ChainId(tx.chain!!))
                val res = rpc.sendRawTransaction(tx.encodeLegacyTxRLP(signature).toHexString())
                val msg = if (chainConfig.explorer != null) {
                    "send 1 ETH (<a href='${chainConfig.explorer}/tx/$res'>view here</a>)"
                } else {
                    "send 1 ETH (transaction: $res)"
                }
                call.respondText("""Swal.fire("Transaction send", "$msg", "success");""")
            }
        }
    }

}

private suspend fun PipelineContext<Unit, ApplicationCall>.render(prefillAddress: String?, siteKey: String) {

    call.respondHtml {
        head {
            title { +"FaucETH" }

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
            div(classes = "container") {
                div {
                    form {
                        id = "mainForm"
                        a(href = "https://github.com/komputing/FaucETH", classes = "github-fork-ribbon fixed") {
                            attributes["title"] = "Fork me on GitHub"
                            attributes["data-ribbon"] = "Fork me on GitHub"
                            +"Fork me on Fork me on GitHub"
                        }
                        h1(classes = "center") {
                            +"FaucETH"
                        }
                        br
                        input(classes = "input") {
                            name = ADDRESS_KEY
                            value = prefillAddress ?: ""
                            placeholder = "Please enter an address"
                        }
                        br
                        br
                        div(classes = "h-captcha center") {
                            attributes["data-sitekey"] = siteKey
                        }
                    }
                    br
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

