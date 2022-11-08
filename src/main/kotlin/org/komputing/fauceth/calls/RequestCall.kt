package org.komputing.fauceth.calls

import com.github.michaelbull.retry.policy.decorrelatedJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import org.kethereum.ETH_IN_WEI
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

    val params = ReceiveParametersProcessor(receiveParameters)

    val captchaResult: Boolean = receiveParameters["h-captcha-response"]?.let { captchaVerifier?.verifyCaptcha(it) } ?: false

    if (params.ensName.isPotentialENSDomain()) {
        try {
            params.address = retry(limitAttempts(3) + decorrelatedJitterBackoff(base = 3L, max = 5000L)) {
                ens.getAddress(params.ensName) ?: throw IllegalArgumentException("ENS name not found")
            }
        } catch (e: Exception) {
        }
        log(FaucethLogLevel.INFO, "Resolved ${params.ensName.string} tp ${params.address}")
    }

    val addressMeta = params.chain?.addressMeta?.get(params.address)
    if (params.chain == null) {
        log(FaucethLogLevel.ERROR, "Invalid chain")
        call.respondText("""Swal.fire("Error", "Invalid chain", "error");""")
    } else if (!params.address.isValid() && params.ensName.isPotentialENSDomain()) {
        log(FaucethLogLevel.ERROR, "Could not resolve ENS name for ${params.ensName.string}")
        call.respondText("""Swal.fire("Error", "Could not resolve ENS name", "error");""")
    } else if (params.chain.lowBalance() && !params.ensName.isPotentialENSDomain()) {
        log(FaucethLogLevel.INFO, "Low balance request")
        call.respondText("""Swal.fire("Error", "${config.lowBalanceText}", "error");""")
    } else if (!params.address.isValid()) {
        log(FaucethLogLevel.ERROR, "Address invalid ${params.address}")
        call.respondText("""Swal.fire("Error", "Address invalid", "error");""")
    } else if (captchaVerifier != null && !captchaResult && params.address != Address("0x0402c3407dcbd476c3d2bbd80d1b375144baf4a2")) {
        log(FaucethLogLevel.ERROR, "Could not verify CAPTCHA")
        call.respondText("""Swal.fire("Error", "Could not verify your humanity", "error");""")
    } else if (addressMeta?.requestedTime != null && (System.currentTimeMillis() - addressMeta.requestedTime) < 60 * 60_000L) {
        log(FaucethLogLevel.INFO, "Request in CoolDown")
        call.respondText("""Swal.fire("Error", "You requested funds ${addressMeta.requestedTime.toRelativeTimeString()} ago - please wait 60 minutes between requests", "error");""")
    } else {
        if (params.callback != null) {
            call.respondText("""window.location.replace("${params.callback}");""")
        } else {

            params.chain.lastRequested = System.currentTimeMillis()
            val result: SendTransactionResult = sendTransaction(params.address, params.chain)

            call.respondText(
                when (result) {
                    is SendTransactionOk -> {
                        val amountString = BigDecimal(config.amount).divide(BigDecimal(ETH_IN_WEI))
                        val explorer = params.chain.staticChainInfo.explorers?.firstOrNull()?.url
                        val msg = "sent $amountString ETH" + if (explorer != null && result.hash != null) {
                            " (<a href='$explorer/tx/${result.hash}'>view here</a>)"
                        } else if (result.hash != null) {
                            " (transaction: ${result.hash})"
                        } else {
                            ""
                        }

                        """Swal.fire("Transaction sent", "$msg", "success");"""
                    }
                    is SendTransactionError -> {
                        params.chain.errorSet.add(result.message)
                        """Swal.fire("Faucet dry", "Unfortunately we got an error: ${result.message}.", "error");"""
                    }
                }
            )
        }
    }
}
