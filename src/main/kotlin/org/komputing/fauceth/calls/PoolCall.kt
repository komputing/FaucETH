package org.komputing.fauceth.calls

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import org.kethereum.model.Transaction
import org.komputing.fauceth.ExtendedChainInfo
import org.komputing.fauceth.FaucethLogLevel
import org.komputing.fauceth.chains
import org.komputing.fauceth.lastPoolClean
import org.komputing.fauceth.util.getTitle
import org.komputing.fauceth.util.keyValueHTML
import org.komputing.fauceth.util.log
import org.komputing.fauceth.util.toRelativeTimeString

internal suspend fun PipelineContext<Unit, ApplicationCall>.poolCall() {
    log(FaucethLogLevel.VERBOSE, "Serving /pool")
    val chain = call.parameters["chain"]
    call.respondHtml {
        head {
            title { +"${getTitle()} Pool" }

            styleLink("/static/css/status.css")
        }
        body {
            keyValueHTML("last clean") { +lastPoolClean.toRelativeTimeString() }
            val filteredChains = if (chain == null) chains else chains.filter { it.staticChainInfo.chainId.toString() == chain }
            filteredChains.forEach { chainInfo ->
                if (chain == null) {
                    h1 {
                        +chainInfo.staticChainInfo.name
                    }
                }
                chainInfo.addressMeta.forEach { (address, addressInfo) ->
                    a(href = chainInfo.staticChainInfo.explorers?.firstOrNull()?.url?.let { "$it/address/$address" }) {
                        +address.toString()
                    }
                    +" ${addressInfo.requestedTime.toRelativeTimeString()}"
                    br
                    addressInfo.pendingTxList.forEach {
                        +"P:"
                        showTx(it, chainInfo)
                        br
                    }
                    addressInfo.confirmedTx?.let {
                        +"C:"
                        showTx(it, chainInfo)
                    }

                    hr {}
                }
            }
        }
    }
}

private fun BODY.showTx(tx: Transaction, chainInfo: ExtendedChainInfo) {
    tx.gasLimit?.let { +"GasLimit:$it " }
    tx.gasPrice?.let { +"GasPrice:$it " }
    tx.maxFeePerGas?.let { +"MaxFeePerGas:$it " }
    tx.maxPriorityFeePerGas?.let { +"MaxPrio:$it " }
    tx.nonce?.let { +"Nonce:$it " }
    tx.creationEpochSecond?.let { +"T:${it.toRelativeTimeString()} " }
    tx.txHash?.let { hash ->
        a(href = chainInfo.staticChainInfo.explorers?.firstOrNull()?.url?.let { "$it/tx/$hash" }) {
            +hash
        }
    }
}