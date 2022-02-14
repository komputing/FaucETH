package org.komputing.fauceth.util

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.StopRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import org.kethereum.rpc.EthereumRPCException

val handle1559NotAvailable: RetryPolicy<Throwable> = {
    if (reason.isUnRecoverableEIP1559Error()) StopRetrying else ContinueRetrying
}

val noRetryWhenNotRecoverable: RetryPolicy<Throwable> = {
    if ((reason as? EthereumRPCException)?.isKnownOrUnderpriced() == true) StopRetrying else ContinueRetrying
}

private fun EthereumRPCException.isKnownOrUnderpriced() = message == "already known" ||  message == "nonce too low" || message.contains("underpriced")