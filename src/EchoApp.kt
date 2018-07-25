package ai.kortnevdmitriy

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.util.ioCoroutineDispatcher
import kotlinx.coroutines.experimental.io.readUTF8Line
import kotlinx.coroutines.experimental.io.writeStringUtf8
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import java.io.InputStream
import java.util.*
import kotlin.coroutines.experimental.buildSequence

/**
 * Two mains are provided, you must first start EchoApp.Server, and then EchoApp.Client.
 * You can also start EchoApp.Server and then use a telnet client to connect to the echo server.
 */
object EchoApp {
	val selectorManager = ActorSelectorManager(ioCoroutineDispatcher)
	val DefaultPort = 9002
	
	object Server {
		@JvmStatic
		fun main(args: Array<String>) {
			runBlocking {
				val serverSocket = aSocket(selectorManager).tcp().bind(port = DefaultPort)
				println("Echo Server listening at ${serverSocket.localAddress}")
				while (true) {
					val socket = serverSocket.accept()
					println("Accepted $socket")
					launch {
						val read = socket.openReadChannel()
						val write = socket.openWriteChannel(autoFlush = true)
						try {
							while (true) {
								val line = read.readUTF8Line()
								write.writeStringUtf8("$line\n")
							}
						} catch (e: Throwable) {
							socket.close()
						}
					}
				}
			}
		}
	}
	
	object Client {
		@JvmStatic
		fun main(args: Array<String>) {
			runBlocking {
				val socket = aSocket(selectorManager).tcp().connect("127.0.0.1", port = DefaultPort)
				val read = socket.openReadChannel()
				val write = socket.openWriteChannel(autoFlush = true)
				
				launch {
					while (true) {
						val line = read.readUTF8Line()
						println("server: $line")
					}
				}
				
				for (line in System.`in`.lines()) {
					println("client: $line")
					write.writeStringUtf8("$line\n")
				}
			}
		}
		
		private fun InputStream.lines() = Scanner(this).lines()
		
		private fun Scanner.lines() = buildSequence {
			while (hasNext()) {
				yield(readLine())
			}
		}
	}
}
