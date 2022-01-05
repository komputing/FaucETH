package org.komputing.fauceth.util

import java.math.BigInteger
import java.math.BigInteger.ONE
import java.util.concurrent.atomic.AtomicReference

class AtomicNonce(initial: BigInteger) {
    private val current = AtomicReference(initial)

    fun getAndIncrement(): BigInteger = current.getAndAccumulate(ONE) { previous, x -> previous.add(x) }
    fun get(): BigInteger = current.get()
    fun setPotentialNewMax(new: BigInteger): BigInteger = current.getAndAccumulate(new) { previous, x -> previous.max(x) }
}