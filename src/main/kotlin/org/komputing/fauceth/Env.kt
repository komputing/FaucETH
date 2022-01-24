package org.komputing.fauceth

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import org.ethereum.lists.chains.model.Chain
import org.kethereum.crypto.toAddress
import org.kethereum.ens.ENS
import org.kethereum.model.Address
import org.kethereum.model.Transaction
import org.kethereum.rpc.*
import org.kethereum.rpc.min3.getMin3RPC
import org.komputing.fauceth.FaucethLogLevel.*
import org.komputing.fauceth.util.AtomicNonce
import org.komputing.fauceth.util.log
import org.komputing.kaptcha.HCaptcha
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.math.BigInteger.ONE
import java.util.*
import kotlin.system.exitProcess

const val ADDRESS_KEY = "address"
const val CALLBACK_KEY = "callback"
const val CHAIN_KEY = "chain"

val configPath = File("/config").takeIf { it.exists() } ?: File(".")

val ens = ENS(getMin3RPC())
val config = FaucethConfig()
val captchaVerifier = config.hcaptchaSecret?.let { HCaptcha(it) }

val okHttpClient = OkHttpClient.Builder().build()


private val chainsDefinitionFile = File(configPath, "chains.json").also {
    if (!it.exists()) {
        it.createNewFile()

        val request = Request.Builder().url("https://chainid.network/chains_pretty.json").build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            fail("could not download chains.json")
        }
        FileOutputStream(it).use { fos ->
            val body = response.body
            if (body == null) {
                fail("could not download chains.json")
            } else {
                fos.write(body.bytes())
            }
        }
    }
}

private val moshi = Moshi.Builder().build()
private var listMyData = Types.newParameterizedType(MutableList::class.java, Chain::class.java)
var chainsAdapter: JsonAdapter<List<Chain>> = moshi.adapter(listMyData)

val unfilteredChains = chainsAdapter.fromJson(chainsDefinitionFile.source().buffer()) ?: fail("Could not read chains.json")

data class AddressInfo(
    var requestedTime: Long,
    val pendingTxList: MutableSet<Transaction> = mutableSetOf(),
    var confirmedTx: Transaction? = null
)

class ExtendedChainInfo(
    val staticChainInfo: Chain,
    val pendingNonce: AtomicNonce,
    val confirmedNonce: AtomicNonce,
    val rpc: EthereumRPC,
    var useEIP1559: Boolean = true, // be optimistic - fallback when no 1559
    var lastSeenBalance: BigInteger? = null,
    var lastRequested: Long? = null,
    var lastConfirmation: Long? = null,
    val addressMeta: MutableMap<Address, AddressInfo> = Collections.synchronizedMap(mutableMapOf()),
    val errorSet: MutableSet<String> = mutableSetOf()
)

val chains = unfilteredChains.filter { config.chains.contains(BigInteger.valueOf(it.chainId)) }.map {
    val rpcURL = it.rpc.first().replace("\${INFURA_API_KEY}", config.infuraProject ?: "none")

    val rpc = if (config.logging == VERBOSE) {
        BaseEthereumRPC(ConsoleLoggingTransportWrapper(HttpTransport(rpcURL)))
    } else {
        HttpEthereumRPC(rpcURL)
    }

    var initialNonce: BigInteger? = null

    while (initialNonce == null) {
        log(INFO, "Fetching initial nonce for chain ${it.name}")
        initialNonce = rpc.getTransactionCount(config.keyPair.toAddress())
    }

    log(INFO, "Got initial nonce for chain ${it.name}: $initialNonce for address ${config.keyPair.toAddress()}")

    ExtendedChainInfo(
        it,
        confirmedNonce = AtomicNonce(initialNonce.minus(ONE)),
        pendingNonce = AtomicNonce(initialNonce),
        rpc = rpc
    )
}

val keywords = listOf(
    listOf("fauceth", "faucet"),
    config.keywords,
    getChainsKeywords { it.staticChainInfo.name },
    getChainsKeywords { it.staticChainInfo.shortName },
    getChainsKeywords { it.staticChainInfo.title }
).flatten().joinToString(",")

private fun getChainsKeywords(function: (ExtendedChainInfo) -> String?) = chains.mapNotNull(function) + chains.mapNotNull(function).map { "$it faucet" }

internal fun fail(msg: String): Nothing {
    println(msg)
    exitProcess(1)
}

var lastPoolClean = System.currentTimeMillis()

