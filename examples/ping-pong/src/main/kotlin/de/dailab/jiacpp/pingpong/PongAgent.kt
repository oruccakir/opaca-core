package de.dailab.jiacpp.pingpong

import de.dailab.jiacpp.container.AbstractContainerizedAgent
import de.dailab.jiacpp.container.Invoke
import de.dailab.jiacpp.model.Action
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacpp.util.RestHelper
import de.dailab.jiacvi.behaviour.act
import kotlin.random.Random


class PongAgent: AbstractContainerizedAgent(name="pong-agent-${Random.nextInt()}") {

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("PongAction", mapOf(Pair("request", "Int"), Pair("offer", "Int")), "String")
        )
    )

    override fun behaviour() = act {

        listen<Message>("pong-channel") {
            // listen to ping message, send offer to ping agent
            val ping = RestHelper.mapper.convertValue(it.payload, Messages.PingMessage_Java::class.java)
            log.info("Received Ping $ping")

            val offer = Random.nextInt(0, 1000)
            val pong = Messages.PongMessage_Java(ping.request, name, offer)
            log.info("Sending Pong $pong")
            sendOutboundMessage(it.replyTo, pong)
        }

        respond<Invoke, Any?> {
            log.info("Received Invoke $it")
            when (it.name) {
                "PongAction" -> pongAction(it.parameters["request"]!!.asInt(), it.parameters["offer"]!!.asInt())
                else -> null
            }
        }

    }

    private fun pongAction(request: Int, offer: Int): String {
        log.info("Invoked my action for request: $request, offer: $offer")
        return "Executed request $request for offer $offer"
    }

}