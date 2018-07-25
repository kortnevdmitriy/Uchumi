package ai.kortnevdmitriy

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.network.util.ioCoroutineDispatcher
import kotlinx.coroutines.experimental.io.readRemaining
import kotlinx.coroutines.experimental.io.writeStringUtf8
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.io.core.readBytes

object TlsRawSocket {
	@JvmStatic
	fun main(args: Array<String>) {
		runBlocking {
			val selectorManager = ActorSelectorManager(ioCoroutineDispatcher)
			val socket = aSocket(selectorManager).tcp().connect("www.google.com", port = 443).tls()
			val write = socket.openWriteChannel()
			val LINE = "\r\n"
			write.writeStringUtf8("GET / HTTP/1.1${LINE}Host: www.google.com${LINE}Connection: close${LINE}${LINE}")
			write.flush()
			println(socket.openReadChannel().readRemaining().readBytes().toString(Charsets.UTF_8))
		}
	}
}
