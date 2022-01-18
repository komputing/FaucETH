package org.komputing.fauceth

import com.natpryce.konfig.*
import org.ethereum.lists.chains.model.Chain
import org.kethereum.ETH_IN_WEI
import org.kethereum.crypto.createEthereumKeyPair
import org.kethereum.crypto.toECKeyPair
import org.kethereum.model.ECKeyPair
import org.kethereum.model.PrivateKey
import java.io.File
import java.io.IOException
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

    val chains: List<BigInteger> = config.getOrNull(Key("app.chains", stringType))?.let { chainIdString ->
        chainIdString.split(",").map { BigInteger(it) }
    } ?: emptyList()

    val hcaptchaSecret = config.getOrNull(Key("hcaptcha.secret", stringType))
    val hcaptchaSiteKey = config.getOrNull(Key("hcaptcha.sitekey", stringType))

    val infuraProject = config.getOrNull(Key("infura.projectid", stringType))

    val keywords = config.getOrNull(Key("app.keywords", stringType))?.split(",") ?: emptyList()

    val port = config.getOrElse(Key("app.port", intType), 8080)

    val appTitle = config.getOrElse(Key("app.title", stringType), "FaucETH")
    val appHeroImage = config.getOrNull(Key("app.imageURL", stringType))
    val amount = BigInteger(config.getOrNull(Key("app.amount", stringType)) ?: "$ETH_IN_WEI")

    val logging = try {
        config.getOrNull(Key("app.logging", stringType))?.let {
            FaucethLogLevel.valueOf(it.uppercase())
        } ?: FaucethLogLevel.INFO
    } catch (e: IllegalArgumentException) {
        println("value for app.logging invalid - possible values: " + FaucethLogLevel.values().joinToString(","))
        exitProcess(1)
    }
}