package org.komputing.fauceth

import io.ktor.application.*
import io.ktor.html.respondHtml
import io.ktor.http.content.*
import io.ktor.request.receiveParameters
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import org.kethereum.DEFAULT_GAS_LIMIT
import org.kethereum.ETH_IN_WEI
import org.kethereum.crypto.createEthereumKeyPair
import org.kethereum.crypto.toAddress
import org.kethereum.crypto.toECKeyPair
import org.kethereum.eip155.signViaEIP155
import org.kethereum.extensions.transactions.encode
import org.kethereum.extensions.transactions.encodeLegacyTxRLP
import org.kethereum.model.*
import org.kethereum.rpc.HttpEthereumRPC
import org.walleth.khex.toHexString
import java.io.File
import java.math.BigInteger
import java.math.BigInteger.ZERO

const val ADDRESS_KEY = "address"
val keystoreFile = File("fauceth_keystore.json")
lateinit var keyPair: ECKeyPair

fun main(args: Array<String>) {
    if (!keystoreFile.exists()) {
        keyPair = createEthereumKeyPair()
        keystoreFile.createNewFile()
        keystoreFile.writeText(keyPair.privateKey.key.toString())
    } else {
        keyPair = PrivateKey(keystoreFile.readText().toBigInteger()).toECKeyPair()
    }

    System.out.println("address" + keyPair.toAddress())
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    routing {
        static("/static") {
            resources("files")
        }
        get("/") {
            val address = call.request.queryParameters[ADDRESS_KEY]
            render(address, null)
        }
        post("/") {
            val address = call.receiveParameters()[ADDRESS_KEY]
            render(null, address)
        }

    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.render(prefillAddress: String?, postAddress: String?) {

    call.respondHtml {
        head {
            title { +"FaucETH" }

            script(src = "https://js.hcaptcha.com/1/api.js") {}

            styleLink("/static/css/sweetalert2.min.css")
            script(src = "/static/js/sweetalert2.all.min.js") {}
        }
        body {
            p {
                +"FaucETH"
            }
            form {
                input {
                    name = ADDRESS_KEY
                    value = prefillAddress ?: postAddress ?: ""
                    placeholder = "Please enter an address"
                }
                br
                postButton {
                    +"Request funds"
                }
                div(classes = "h-captcha") {
                    attributes["data-sitekey"] = "1559a32b-7448-49f3-bdd5-cbb8aabb0a4b"
                }
            }

            if (postAddress != null) {
                if (postAddress.length != 42) {
                    alert("Invalid address", "$postAddress is not a valid address", "error")
                } else {

                    val rpc = HttpEthereumRPC("https://rpc.kintsugi.themerge.dev")
                    val nonce = rpc.getTransactionCount(keyPair.toAddress())
                    val tx = createEmptyTransaction().apply {
                        to = Address(postAddress)
                        value = ETH_IN_WEI
                        this.nonce = nonce
                        gasLimit = DEFAULT_GAS_LIMIT
                        gasPrice = BigInteger.valueOf(1000000000)
                        chain = BigInteger.valueOf(1337702)
                    }

                    val signature=tx.signViaEIP155(keyPair, ChainId(tx.chain!!))
                    val res = rpc.sendRawTransaction(tx.encodeLegacyTxRLP(signature).toHexString())
                    alert("Success", "send 1 ETH $res", "success")
                }
            }
        }
    }
}

private fun BODY.alert(title: String, message: String, type: String) {
    unsafe {
        +"""
         <script>
         Swal.fire("$title","$message","$type")
         </script
         """.trimIndent()
    }
}
