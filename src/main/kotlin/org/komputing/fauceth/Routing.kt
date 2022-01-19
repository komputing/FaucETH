package org.komputing.fauceth

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*
import org.komputing.fauceth.calls.statusCall
import org.komputing.fauceth.calls.indexCall
import org.komputing.fauceth.calls.requestCall
import org.komputing.fauceth.calls.addressCall

internal fun Application.configureRouting() {
    routing {
        static("/static") {
            staticRootFolder
            resources("files")
        }

        get("/") { indexCall() }
        get("/status") { statusCall() }
        get("/address") { addressCall() }

        post("/request") { requestCall() }
    }
}

