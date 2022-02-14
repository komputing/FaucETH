package org.komputing.fauceth.util

import org.kethereum.rpc.EthereumRPCException

fun Throwable.isUnRecoverableEIP1559Error() = this is EthereumRPCException &&
        (message == "the method eth_feeHistory does not exist/is not available") ||
        (message == "rpc method is not whitelisted")