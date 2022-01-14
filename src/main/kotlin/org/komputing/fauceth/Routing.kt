package org.komputing.fauceth

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*
import org.komputing.fauceth.calls.adminCall
import org.komputing.fauceth.calls.indexCall
import org.komputing.fauceth.calls.requestCall

internal fun Application.configureRouting() {
    routing {
        static("/static") {
            staticRootFolder
            resources("files")
        }
        get("/") {
            indexCall()
        }
        get("/admin") {
            adminCall()
        }
        post("/request") {
            requestCall()
        }
    }
}

