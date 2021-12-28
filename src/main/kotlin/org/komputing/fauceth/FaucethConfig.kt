package org.komputing.fauceth

import com.natpryce.konfig.*
import org.kethereum.ETH_IN_WEI
import org.kethereum.crypto.createEthereumKeyPair
import org.kethereum.crypto.toECKeyPair
import org.kethereum.model.ECKeyPair
import org.kethereum.model.PrivateKey
import java.io.File
import java.lang.IllegalArgumentException
import java.math.BigInteger
import kotlin.system.exitProcess

enum class FaucethLogLevel {
    NONE,
    ERROR,
    INFO,
    VERBOSE
}

class FaucethConfig {
    private val config = ConfigurationProperties.systemProperties() overriding
            EnvironmentVariables() overriding
            ConfigurationProperties.fromOptionalFile(File("fauceth.properties"))

    val keyPair: ECKeyPair = config.getOrNull(Key("app.ethkey", stringType))?.let {
        PrivateKey(it.toBigInteger(16)).toECKeyPair()
    } ?: if (!keystoreFile.exists()) {
        createEthereumKeyPair().also {
            keystoreFile.createNewFile()
            keystoreFile.writeText(it.privateKey.key.toString())
        }
    } else {
        PrivateKey(keystoreFile.readText().toBigInteger()).toECKeyPair()
    }

    val hcaptchaSecret = config[Key("hcaptcha.secret", stringType)]
    val hcaptchaSiteKey = config[Key("hcaptcha.sitekey", stringType)]

    val appTitle = config.getOrElse(Key("app.title", stringType), "FaucETH")
    val appHeroImage = config.getOrNull(Key("app.imageURL", stringType))
    val amount = BigInteger(config.getOrNull(Key("app.amount", stringType)) ?: "$ETH_IN_WEI")

    val chainRPCURL = config[Key("chain.rpc", stringType)]
    val chainExplorer = config.getOrNull(Key("chain.explorer", stringType))
    val chainId = BigInteger(config[Key("chain.id", stringType)])

    val logging = try {
        config.getOrNull(Key("app.logging", stringType))?.let {
            FaucethLogLevel.valueOf(it.uppercase())
        }?:FaucethLogLevel.INFO
    } catch (e: IllegalArgumentException) {
        println("value for app.logging invalid - possible values: " + FaucethLogLevel.values().joinToString(","))
        exitProcess(1)
    }
}