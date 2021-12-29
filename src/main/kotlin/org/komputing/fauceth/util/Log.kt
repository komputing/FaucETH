package org.komputing.fauceth.util

import org.komputing.fauceth.FaucethLogLevel
import org.komputing.fauceth.config

fun log(level: FaucethLogLevel, msg: String) {
    if (config.logging >= level) println("$level: $msg")
}