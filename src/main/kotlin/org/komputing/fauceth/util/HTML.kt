package org.komputing.fauceth.util

import kotlinx.html.BODY
import kotlinx.html.b
import kotlinx.html.br

internal fun BODY.keyValueHTML(key: String, value: String) {
    b {
        +"$key: "
    }
    +value
    br
}