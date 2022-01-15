package org.komputing.fauceth.util

import org.komputing.fauceth.ExtendedChainInfo
import org.komputing.fauceth.config

internal fun getTitle(requestedChain: ExtendedChainInfo? = null) = config.appTitle
    .replace("%CHAINNAME", requestedChain?.staticChainInfo?.name ?: "")
    .replace("%CHAINTITLE", requestedChain?.staticChainInfo?.title ?: "")
    .replace("%CHAINSHORTNAME", requestedChain?.staticChainInfo?.shortName ?: "")
    .trim()