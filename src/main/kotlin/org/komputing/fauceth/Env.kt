package org.komputing.fauceth

import org.kethereum.ens.ENS
import org.kethereum.rpc.BaseEthereumRPC
import org.kethereum.rpc.ConsoleLoggingTransportWrapper
import org.kethereum.rpc.HttpEthereumRPC
import org.kethereum.rpc.HttpTransport
import org.kethereum.rpc.min3.getMin3RPC
import org.komputing.fauceth.FaucethLogLevel.*
import org.komputing.kaptcha.HCaptcha
import java.io.File

const val ADDRESS_KEY = "address"
val keystoreFile = File("fauceth_keystore.json")
val ens = ENS(getMin3RPC())
val config = FaucethConfig()
val captchaVerifier = HCaptcha(config.hcaptchaSecret)

val rpc = if (config.logging == VERBOSE) {
    BaseEthereumRPC(ConsoleLoggingTransportWrapper(HttpTransport(config.chainRPCURL)))
} else {
    HttpEthereumRPC(config.chainRPCURL)
}