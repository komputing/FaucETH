package org.komputing.fauceth.util

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.StopRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import org.kethereum.rpc.EthereumRPCException

val handle1559NotAvailable: RetryPolicy<Throwable> = {
    if (reason is EthereumRPCException &&
        (reason.message == "the method eth_feeHistory does not exist/is not available") ||
        (reason.message == "rpc method is not whitelisted")
    ) StopRetrying else ContinueRetrying
}

val noRetryWhenKnown: RetryPolicy<Throwable> = {
    if (reason is EthereumRPCException && reason.message == "already known") StopRetrying else ContinueRetrying
}