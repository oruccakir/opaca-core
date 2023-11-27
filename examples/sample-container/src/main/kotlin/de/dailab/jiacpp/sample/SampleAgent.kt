package de.dailab.jiacpp.sample

import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.container.AbstractContainerizedAgent
import de.dailab.jiacpp.container.Invoke
import de.dailab.jiacpp.container.StreamInvoke
import de.dailab.jiacpp.model.Action
import de.dailab.jiacpp.model.Stream
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacvi.behaviour.act

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class SampleAgent(name: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()

    override fun preStart() {
        super.preStart()
        addAction(Action("DoThis", mapOf("message" to "String", "sleep_seconds" to "Int"), "String")) {
            actionDoThis(it["message"]!!.asText(), it["sleep_seconds"]!!.asInt())
        }
        addAction(Action("GetInfo", mapOf(), "Map")) {
            actionGetInfo()
        }
        addAction(Action("GetEnv", mapOf(), "Map")) {
            actionGetEnv()
        }
        addAction(Action("Add", mapOf("x" to "String", "y" to "Int"), "Int")) {
            actionAdd(it["x"]!!.asInt(), it["y"]!!.asInt())
        }
        addAction(Action("Fail", mapOf(), "void")) {
            actionFail()
        }
        addAction(Action("CreateAction", mapOf("name" to "String", "notify" to "Boolean"), "void")) {
            createAction(it["name"]!!.asText(), it["notify"]!!.asBoolean())
        }
        addAction(Action("SpawnAgent", mapOf("name" to "String"), "void")) {
            spawnAgent(it["name"]!!.asText())
        }
        addAction(Action("Deregister", mapOf(), "void")) {
            deregister(false)
        }
    }

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        actions,
        listOf(
            Stream("GetStream", Stream.Mode.GET)
        )
    )

    override fun behaviour() = super.behaviour().and(act {

        on<Message> {
            log.info("ON $it")
            lastMessage = it.payload
        }

        listen<Message>("topic") {
            log.info("LISTEN $it")
            lastBroadcast = it.payload
        }

        respond<StreamInvoke, Any?> {
            when (it.name) {
                "GetStream" -> actionGetStream()
                else -> null
            }
        }

    })


    private fun actionGetStream(): ByteArrayInputStream {
        val data = "{\"key\":\"value\"}".toByteArray(Charset.forName("UTF-8"))
        return ByteArrayInputStream(data)
    }

    private fun actionDoThis(message: String, sleep_seconds: Int): String {
        log.info("in 'DoThis' action, waiting...")
        println(message)
        Thread.sleep(1000 * sleep_seconds.toLong())
        log.info("done waiting")
        return "Action 'DoThis' of $name called with message=$message and sleep_seconds=$sleep_seconds"
    }

    private fun actionAdd(x: Int, y: Int) = x + y

    private fun actionFail() {
        throw RuntimeException("Action Failed (as expected)")
    }

    private fun actionGetInfo() = mapOf(
        Pair("name", name),
        Pair("lastMessage", lastMessage),
        Pair("lastBroadcast", lastBroadcast),
        Pair(AgentContainerApi.ENV_CONTAINER_ID, System.getenv(AgentContainerApi.ENV_CONTAINER_ID)),
        Pair(AgentContainerApi.ENV_PLATFORM_URL, System.getenv(AgentContainerApi.ENV_PLATFORM_URL)),
        Pair(AgentContainerApi.ENV_TOKEN, System.getenv(AgentContainerApi.ENV_TOKEN))
    )

    private fun actionGetEnv() = System.getenv()

    private fun createAction(name: String, notify: Boolean) {
        addAction(Action(name, mapOf(), "String")) {
            "Called extra action $name"
        }
        register(notify)
    }

    private fun spawnAgent(name: String) {
        system.spawnAgent(SampleAgent(name))
    }

}
