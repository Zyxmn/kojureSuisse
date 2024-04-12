package com.kojureSuisse

import com.kojureSuisse.formats.JacksonMessage
import com.kojureSuisse.formats.jacksonMessageLens
import com.kojureSuisse.models.IndexViewModel
import com.kojureSuisse.models.LoginViewModel
import org.http4k.core.*
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.body.form
import org.http4k.core.cookie.cookie
import org.http4k.filter.DebuggingFilters.PrintRequest
import org.http4k.lens.Header
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Undertow
import org.http4k.server.asServer
import org.http4k.template.JTETemplates
import org.http4k.template.viewModel

data class User(val username: String, val password: String, val displayName: String) {
    constructor(username: String, password: String) : this(username, password, username) {}
}

fun getApp(users: Map<String, User>): HttpHandler {
    val renderer = JTETemplates().CachingClasspath()
    val view = Body.viewModel(renderer, TEXT_HTML).toLens()

    return routes(
        "/formats/json/jackson" bind GET to {
            Response(OK).with(jacksonMessageLens of JacksonMessage("Barry", "Hello there!"))
        },

        "/" bind GET to {

            it.cookie("session")?.let {
                Response(OK).with(view of IndexViewModel)
            }
                ?: Response(FOUND).with(Header.LOCATION of Uri.of("/login"))
        },

        "/login" bind GET to {
            Response(OK).with(view of LoginViewModel(null))
        },

        "/login" bind POST to {
            val username = it.form("username")
            val password = it.form("password")

            if (users[username]?.password != password) {
                Response(OK).with(view of LoginViewModel("incorrect username or password"))
            } else {
                Response(OK).with(view of LoginViewModel("todo: login")) // TODO
            }
        },
    )
}

fun main() {
    val users = mapOf(
        "fraser1" to User("fraser1", "123"),
        "fraser2" to User("fraser2", "1234"),
        "fraser3" to User("fraser3", "12345"),
    )
    val printingApp: HttpHandler = PrintRequest().then(getApp(users))

    val server = printingApp.asServer(Undertow(9000)).start()

    println("Server started on " + server.port())
}
