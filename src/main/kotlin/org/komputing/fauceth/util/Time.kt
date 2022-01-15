package org.komputing.fauceth.util

private fun StringBuilder.maybeAppendTimePart(part: Long, unit: String) {
    if (part > 0) {
        append(part)
        append(unit)
        append(" ")
    }
}

fun Long.toTimeString() = StringBuilder().also {
    it.maybeAppendTimePart((this / 60 / 60) % 60, "h")
    it.maybeAppendTimePart((this / 60) % 60, "m")
    it.maybeAppendTimePart(this % 60, "s")
}.toString().trimEnd()

fun Long.toRelativeTimeString() = ((System.currentTimeMillis() - this) / 1000).toTimeString()

