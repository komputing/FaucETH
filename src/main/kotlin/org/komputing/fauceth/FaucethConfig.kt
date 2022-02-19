package org.komputing.fauceth

import com.natpryce.konfig.*
import org.kethereum.ETH_IN_WEI
import org.kethereum.crypto.createEthereumKeyPair
import org.kethereum.crypto.toAddress
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

    private val configFileName = "fauceth.properties"
    private val configFile = File(configPath, configFileName).takeIf { it.exists() }
        ?: File(configFileName).takeIf { it.exists() }
        ?: File(configPath, configFileName).also {
            it.createNewFile()
            val key = createEthereumKeyPair()
            it.writeText(File("$configFileName.example").readText().replace("YOUR_ETH_KEY", "0x" + key.privateKey.key.toString(16)))
        }

    private val config = ConfigurationProperties.systemProperties() overriding
            EnvironmentVariables() overriding
            ConfigurationProperties.fromFile(configFile)

    val keyPair: ECKeyPair = config[Key("app.ethkey", stringType)].let {
        PrivateKey(it.removePrefix("0x").toBigInteger(16)).toECKeyPair()
    }

    val address = keyPair.toAddress()

    val chains: List<BigInteger> = config.getOrNull(Key("app.chains", stringType))?.let { chainIdString ->
        chainIdString.split(",").map { BigInteger(it) }
    } ?: emptyList()

    val hcaptchaSecret = config.getOrNull(Key("hcaptcha.secret", stringType))
    val hcaptchaSiteKey = config.getOrNull(Key("hcaptcha.sitekey", stringType))

    val infuraProject = config.getOrNull(Key("infura.projectid", stringType))

    val keywords = config.getOrNull(Key("app.keywords", stringType))?.split(",") ?: emptyList()
    val footerHTML = config.getOrNull(Key("app.footer", stringType))
    val port = config.getOrElse(Key("app.port", intType), 8080)
    val appTitle = config.getOrElse(Key("app.title", stringType), "FaucETH")
    val appHeroImage = config.getOrNull(Key("app.imageURL", stringType))
    val amount = BigInteger(config.getOrNull(Key("app.amount", stringType)) ?: "$ETH_IN_WEI")

    val lowBalanceThreshold = BigInteger(config.getOrNull(Key("app.lowbalance.threshold", stringType)) ?: ("10000" +"0".repeat(18)))
    val lowBalanceText = config.getOrNull(Key("app.lowbalance.text", stringType)) ?: "Low supply -> currently only available for ENS domains."

    val logging = try {
        config.getOrNull(Key("app.logging", stringType))?.let {
            FaucethLogLevel.valueOf(it.uppercase())
        } ?: FaucethLogLevel.INFO
    } catch (e: IllegalArgumentException) {
        println("value for app.logging invalid - possible values: " + FaucethLogLevel.values().joinToString(","))
        exitProcess(1)
    }
}