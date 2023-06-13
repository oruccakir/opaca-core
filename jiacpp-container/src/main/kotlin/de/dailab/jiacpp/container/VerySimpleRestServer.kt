package de.dailab.jiacpp.container

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.model.Message
import de.dailab.jiacpp.util.RestHelper
import java.net.InetSocketAddress
import java.util.concurrent.Executors


/**
 * Alternative implementation of minimal REST server providing the JIAC++ AgentContainer API.
 * This uses only Java builtin classes, no external libraries, and could thus easily be moved
 * to the jiacpp-model module as a basis for all sorts of Java-based AgentContainer implementations.
 *
 * TODO this does not work properly yet (freezing after first request)
 */
class JiacppServer2(val impl: AgentContainerApi, val port: Int) {

    private val server = HttpServer.create(InetSocketAddress("localhost", port), 0)

    /**
     * servlet handling the different REST routes, delegating to `impl` for actual logic
     */
    private val handler = object: HttpHandler {

        override fun handle(exchange: HttpExchange) {
            println("IN HANDLE FOR ${exchange.requestURI}")
            try {
                val res = when (exchange.requestMethod) {
                    "GET" -> doGet(exchange)
                    "POST" -> doPost(exchange)
                    else -> throw NoSuchMethodException("Method not supported")
                }
                println("RES $res")
                writeResponse(exchange, 200, res)
            } catch (e: Exception) {
                println("ERROR $e")
                val code = when (e) {
                    is NoSuchMethodException -> 405
                    is NoSuchElementException -> 404
                    else -> 500
                }
                val err = mapOf(Pair("details", e.toString()))
                writeResponse(exchange, code, err)
            }
            println("done here")
        }

        private fun writeResponse(exchange: HttpExchange, code: Int, result: Any?) {
            val json = RestHelper.writeJson(result)
            val bytes = json.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        }

        private fun doGet(exchange: HttpExchange): Any? {
            val path = exchange.requestURI.path

            val info = Regex("^/info$").find(path)
            if (info != null) {
                return impl.containerInfo
            }

            val agents = Regex("^/agents$").find(path)
            if (agents != null) {
                return  impl.agents
            }

            val agentWithId = Regex("^/agents/([^/]+)$").find(path)
            if (agentWithId != null) {
                val id = agentWithId.groupValues[1]
                return impl.getAgent(id)
            }

            throw NoSuchElementException("Unknown path: $path")
        }

        private fun doPost(exchange: HttpExchange): Any? {
            val path = exchange.requestURI.path
            val body: String = exchange.requestBody.bufferedReader().use { it.readText() }

            val send = Regex("^/send/([^/]+)$").find(path)
            if (send != null) {
                val id = send.groupValues[1]
                val message = RestHelper.readObject(body, Message::class.java)
                return impl.send(id, message, "", false)
            }

            val broadcast = Regex("^/broadcast/([^/]+)$").find(path)
            if (broadcast != null) {
                val channel = broadcast.groupValues[1]
                val message = RestHelper.readObject(body, Message::class.java)
                return impl.broadcast(channel, message, "", false)
            }

            val invokeAct = Regex("^/invoke/([^/]+)$").find(path)
            if (invokeAct != null) {
                val action = invokeAct.groupValues[1]
                val parameters = RestHelper.readMap(body)
                return impl.invoke(action, parameters, "", false)
            }

            val invokeActOf = Regex("^/invoke/([^/]+)/([^/]+)$").find(path)
            if (invokeActOf != null) {
                val action = invokeActOf.groupValues[1]
                val agentId = invokeActOf.groupValues[2]
                val parameters = RestHelper.readMap(body)
                return impl.invoke(action, parameters, agentId, "", false)
            }

            throw NoSuchElementException("Unknown path: $path")
        }
    }

    fun start() {
        server.createContext("/", handler)
        server.executor = Executors.newFixedThreadPool(10)
        server.start()
    }

    fun stop() {
        server.stop(0)
    }

}
