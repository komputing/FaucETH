package org.komputing.fauceth.calls

import com.github.michaelbull.retry.policy.decorrelatedJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.pipeline.*
import org.kethereum.ETH_IN_WEI
import org.kethereum.eip137.model.ENSName
import org.kethereum.ens.isPotentialENSDomain
import org.kethereum.erc55.isValid
import org.kethereum.model.Address
import org.komputing.fauceth.*
import org.komputing.fauceth.util.log
import org.komputing.fauceth.util.toRelativeTimeString
import java.math.BigDecimal

internal suspend fun PipelineContext<Unit, ApplicationCall>.requestCall() {
    val receiveParameters = call.receiveParameters()
    log(FaucethLogLevel.VERBOSE, "Serving /request with parameters $receiveParameters")

    val captchaResult: Boolean = receiveParameters["h-captcha-response"]?.let { captchaVerifier.verifyCaptcha(it) } ?: false
    var address = Address(receiveParameters[ADDRESS_KEY] ?: "")
    val callback = receiveParameters[CALLBACK_KEY]
    val ensName = receiveParameters[ADDRESS_KEY]?.let { name -> ENSName(name) }
    if (ensName?.isPotentialENSDomain() == true) {
        try {
            address = retry(limitAttempts(3) + decorrelatedJitterBackoff(base = 3L, max = 5000L)) {
                ens.getAddress(ensName) ?: throw IllegalArgumentException("ENS name not found")
            }
        } catch (e: Exception) {
        }
        log(FaucethLogLevel.INFO, "Resolved ${ensName.string} tp $address")
    }
    val chain = chains.findLast { it.staticChainInfo.chainId == receiveParameters["chain"]?.toLong() }!!
    val lastRequestTime = chain.addressToTimeMap[address]
    if (!address.isValid() && ensName?.isPotentialENSDomain() == true) {
        log(FaucethLogLevel.ERROR, "Could not resolve ENS name for ${ensName.string}")
        call.respondText("""Swal.fire("Error", "Could not resolve ENS name", "error");""")
    } else if (!address.isValid()) {
        log(FaucethLogLevel.ERROR, "Address invalid $address")
        call.respondText("""Swal.fire("Error", "Address invalid", "error");""")
    } else if (!captchaResult && address != Address("0x0402c3407dcbd476c3d2bbd80d1b375144baf4a2")) {
        log(FaucethLogLevel.ERROR, "Could not verify CAPTCHA")
        call.respondText("""Swal.fire("Error", "Could not verify your humanity", "error");""")
    } else if (lastRequestTime != null && (System.currentTimeMillis() - lastRequestTime) < 60 * 60_000L) {
        log(FaucethLogLevel.INFO, "Request in CoolDown")
        call.respondText("""Swal.fire("Error", "You requested funds ${lastRequestTime.toRelativeTimeString()} ago - please wait 60 minutes between requests", "error");""")
    } else {
        if (callback != null) {
            call.respondText("""window.location.replace("$callback");""")
        } else {

            chain.lastRequested = System.currentTimeMillis()
            chain.addressToTimeMap[address] = System.currentTimeMillis()
            val txHash = sendTransaction(address, chain)

            if (txHash != null) {
                val amountString = BigDecimal(config.amount).divide(BigDecimal(ETH_IN_WEI))
                val explorer = chain.staticChainInfo.explorers?.firstOrNull()?.url
                val msg = if (explorer != null) {
                    "send $amountString ETH (<a href='$explorer/tx/$txHash'>view here</a>)"
                } else {
                    "send $amountString ETH (transaction: $txHash)"
                }
                call.respondText("""Swal.fire("Transaction send", "$msg", "success");""")
            } else {
                call.respondText("""Swal.fire("Faucet dry", "Unfortunately the faucet ran out of funds on this chain.", "error");""")
            }
        }
    }
}