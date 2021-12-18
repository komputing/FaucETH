package org.komputing.fauceth

import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference

class AtomicNonce(initial: BigInteger) {
    private val current: AtomicReference<BigInteger>

    init {
        current = AtomicReference(initial)
    }

    fun getAndIncrement(): BigInteger =current.getAndAccumulate(BigInteger.ONE) { previous, x -> previous.add(x) }
}