package org.komputing.fauceth

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*
import org.komputing.fauceth.calls.*
import org.komputing.fauceth.calls.addressCall
import org.komputing.fauceth.calls.indexCall
import org.komputing.fauceth.calls.requestCall
import org.komputing.fauceth.calls.statusCall

internal fun Application.configureRouting() {
    routing {
        static("/static") {
            staticRootFolder
            resources("files")
        }

        get("/") { indexCall() }
        get("/status") { statusCall() }
        get("/pool") { poolCall() }
        get("/address") { addressCall() }

        post("/request") { requestCall() }
    }
}

