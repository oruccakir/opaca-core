package de.gtarc.opaca.inventoryorder

import de.gtarc.opaca.container.*
import de.gtarc.opaca.model.Message
import de.gtarc.opaca.util.RestHelper
import de.dailab.jiacvi.behaviour.act
import java.time.Duration
import kotlin.random.Random


class OrderAgent: AbstractContainerizedAgent(name="order-agent") {

    override fun behaviour() = super.behaviour().and(act {

        every(Duration.ofSeconds(5)) {
           
           log.info("Sending order message as broadcast...")
           val order = OrderMessage(1, "pending", "2021-09-01", 10)
           sendOutboundMessage("order-channel",order)

        }

        on<Message> {
           
        }

    })

}
