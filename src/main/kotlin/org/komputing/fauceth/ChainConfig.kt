package org.komputing.fauceth

import java.math.BigInteger

data class ChainConfig(
    val name: String,
    val chainId: BigInteger,
    val explorer: String?,
    val rpc: String
)