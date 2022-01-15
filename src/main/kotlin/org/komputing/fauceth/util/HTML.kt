package org.komputing.fauceth.util

import kotlinx.html.*

internal fun HtmlBlockTag.keyValueHTML(key: String, f: HtmlBlockTag.() -> Unit) {
    b {
        +"$key: "
    }
    f.invoke(this)
    br
}