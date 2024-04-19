package com.kojureSuisse

import com.fasterxml.jackson.annotation.JsonProperty
import com.kojureSuisse.formats.JacksonMessage
import com.kojureSuisse.formats.jacksonMessageLens
import org.http4k.format.Jackson.auto
import com.kojureSuisse.models.IndexViewModel
import com.kojureSuisse.models.LoginViewModel
import com.kojureSuisse.models.ChatViewModel
import org.http4k.core.*
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.filter.DebuggingFilters.PrintRequest
import org.http4k.lens.Header
import org.http4k.routing.bind
import org.http4k.routing.ws.bind as wsbind
import org.http4k.routing.routes
import org.http4k.routing.websockets
import org.http4k.server.PolyHandler
import org.http4k.server.Undertow
import org.http4k.server.asServer
import org.http4k.template.JTETemplates
import org.http4k.template.viewModel
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse

data class User(val username: String, val password: String, val displayName: String) {
    constructor(username: String, password: String) : this(username, password, username)
}

data class ChatMessage(@JsonProperty("chat_message") val chatMessage: String)

fun getApp(users: Map<String, User>): PolyHandler {
    val renderer = JTETemplates().CachingClasspath()
    val view = Body.viewModel(renderer, TEXT_HTML).toLens()

    val messageLens = WsMessage.auto<ChatMessage>().toLens()

    val http = routes(
        "/formats/json/jackson" bind GET to {
            Response(OK).with(jacksonMessageLens of JacksonMessage("Barry", "Hello there!"))
        },

        "/" bind GET to {

            it.cookie("session")?.let {
                Response(OK).with(view of IndexViewModel)
            }
                ?: Response(FOUND).with(Header.LOCATION of Uri.of("/login"))
        },

        "/chat" bind GET to {

            it.cookie("session")?.let {
                Response(OK).with(view of ChatViewModel)
            }
                ?: Response(FOUND).with(Header.LOCATION of Uri.of("/login"))
        },

        "/login" bind GET to {
            Response(OK).with(view of LoginViewModel(null))
        },

        "/login" bind POST to {
            val username = it.form("username")
            val password = it.form("password")

            if (username == null || users[username]?.password != password) {
                Response(OK).with(view of LoginViewModel("incorrect username or password"))
            } else {
                Response(FOUND).with(Header.LOCATION of Uri.of("/")).cookie(Cookie("session", username))
            }
        },
    )

    val ws = websockets(
        "/ws" wsbind { req: Request ->
            req.cookie("session")?.value?.let { username ->
                WsResponse { ws: Websocket ->
                    val name = "yolo"
                    ws.send(WsMessage("hello $name"))

                    ws.onMessage {
                        val chatMessage = messageLens(it)
                        val response = """
                        <div id="chat_room" hx-swap-oob="beforeend">
                          $username: ${chatMessage.chatMessage}
                          <br />
                        </div>
                        """
                        ws.send(WsMessage(response))
                    }
                    ws.onClose { println("$name is closing") }
                }

            } ?: WsResponse { it.send(WsMessage("oh no, a hacker!")) }
        }
    )
    return PolyHandler(PrintRequest().then(http), ws)
}

fun main() {
    val users = mapOf(
        "fraser1" to User("fraser1", "123"),
        "fraser2" to User("fraser2", "1234"),
        "fraser3" to User("fraser3", "12345"),
    )

    val server = getApp(users).asServer(Undertow(9000)).start()

    println("Server started on " + server.port())
}
