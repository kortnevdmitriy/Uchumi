package ai.kortnevdmitriy

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.jetty.Jetty
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.content.CachingOptions
import io.ktor.content.resources
import io.ktor.content.static
import io.ktor.features.*
import io.ktor.gson.gson
import io.ktor.http.*
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.jackson.jackson
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ShutDownUrl
import io.ktor.sessions.*
import io.ktor.util.hex
import io.ktor.websocket.webSocket
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.event.Level
import java.net.URL
import java.time.Duration
import kotlin.collections.set

fun main(args: Array<String>): Unit = io.ktor.server.jetty.DevelopmentEngine.main(args)

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()

data class MySession(val count: Int = 0)

data class JsonSampleClass(val hello: String)

fun Application.module() {
	install(Sessions) {
		cookie<MySession>("MY_SESSION") {
			cookie.extensions["SameSite"] = "lax"
		}
	}
	
	install(Compression) {
		gzip {
			priority = 1.0
		}
		deflate {
			priority = 10.0
			minimumSize(1024) // condition
		}
	}
	
	install(AutoHeadResponse)
	
	install(CallLogging) {
		level = Level.INFO
		filter { call -> call.request.path().startsWith("/") }
	}
	
	install(ConditionalHeaders)
	
	install(CORS) {
		method(HttpMethod.Options)
		method(HttpMethod.Put)
		method(HttpMethod.Delete)
		method(HttpMethod.Patch)
		header(HttpHeaders.Authorization)
		header("MyCustomHeader")
		allowCredentials = true
		anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
	}
	
	install(CachingHeaders) {
		options { outgoingContent ->
			when (outgoingContent.contentType?.withoutParameters()) {
				ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
				else -> null
			}
		}
	}
	
	install(DataConversion)
	
	install(DefaultHeaders) {
		header("X-Engine", "Ktor") // will send this header with each response
	}
	
	install(ForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy
	install(XForwardedHeadersSupport) // WARNING: for security, do not include this if not behind a reverse proxy
	
	install(HSTS) {
		includeSubDomains = true
	}
	
	install(HttpsRedirect) {
		// The port to redirect to. By default 443, the default HTTPS port.
		sslPort = 443
		// 301 Moved Permanently, or 302 Found redirect.
		permanentRedirect = true
	}
	
	install(ShutDownUrl.ApplicationCallFeature) {
		// The URL that will be intercepted (you can also use the application.conf's ktor.deployment.shutdown.url key)
		shutDownUrl = "/ktor/application/shutdown"
		// A function that will be executed to get the exit code of the process
		exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
	}
	
	install(io.ktor.websocket.WebSockets) {
		pingPeriod = Duration.ofSeconds(15)
		timeout = Duration.ofSeconds(15)
		maxFrameSize = Long.MAX_VALUE
		masking = false
	}
	
	install(PartialContent) {
		// Maximum number of ranges that will be accepted from a HTTP request.
		// If the HTTP request specifies more ranges, they will all be merged into a single range.
		maxRangeCount = 10
	}
	
	install(Authentication) {
		basic("myBasicAuth") {
			realm = "Ktor Server"
			validate { if (it.name == "test" && it.password == "password") UserIdPrincipal(it.name) else null }
		}
		
		val myRealm = "MyRealm"
		val usersInMyRealmToHA1: Map<String, ByteArray> = mapOf(
			// pass="test", HA1=MD5("test:MyRealm:pass")="fb12475e62dedc5c2744d98eb73b8877"
			"test" to hex("fb12475e62dedc5c2744d98eb73b8877")
		)
		digest("myDigestAuth") {
			userNameRealmPasswordDigestProvider = { userName, realm ->
				usersInMyRealmToHA1[userName]
			}
		}
	}
	
	install(ContentNegotiation) {
		gson {
		}
		
		jackson {
			enable(SerializationFeature.INDENT_OUTPUT)
		}
	}
	
	val client = HttpClient(Jetty) {
		install(BasicAuth) {
			username = "test"
			password = "pass"
		}
		install(JsonFeature) {
			serializer = GsonSerializer()
		}
	}
	runBlocking {
		val message = client.post<JsonSampleClass> {
			url(URL("http://127.0.0.1:8080/path/to/endpoint"))
			contentType(ContentType.Application.Json)
			body = JsonSampleClass(hello = "world")
		}
	}
	
	routing {
		get("/") {
			call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
		}
		
		// Static feature. Try to access `/static/ktor_logo.svg`
		static("/static") {
			resources("static")
		}
		
		install(StatusPages) {
			exception<AuthenticationException> { cause ->
				call.respond(HttpStatusCode.Unauthorized)
			}
			exception<AuthorizationException> { cause ->
				call.respond(HttpStatusCode.Forbidden)
			}
		}
		
		get("/session/increment") {
			val session = call.sessions.get<MySession>() ?: MySession()
			call.sessions.set(session.copy(count = session.count + 1))
			call.respondText("Counter is ${session.count}. Refresh to increment.")
		}
		
		webSocket("/myws/echo") {
			send(Frame.Text("Hi from server"))
			while (true) {
				val frame = incoming.receive()
				if (frame is Frame.Text) {
					send(Frame.Text("Client said: " + frame.readText()))
				}
			}
		}
		
		authenticate("myBasicAuth") {
			get("/protected/route/basic") {
				val principal = call.principal<UserIdPrincipal>()!!
				call.respondText("Hello ${principal.name}")
			}
		}
		
		authenticate("myDigestAuth") {
			get("/protected/route/digest") {
				val principal = call.principal<UserIdPrincipal>()!!
				call.respondText("Hello ${principal.name}")
			}
		}
		
		get("/json/gson") {
			call.respond(mapOf("hello" to "world"))
		}
		
		get("/json/jackson") {
			call.respond(mapOf("hello" to "world"))
		}
	}
}

