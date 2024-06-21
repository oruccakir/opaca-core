package de.gtarc.opaca.inventoryorder

import de.dailab.jiacvi.behaviour.act
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Message
import de.gtarc.opaca.model.Parameter
import de.gtarc.opaca.util.RestHelper
import kotlin.random.Random


class InventoryAgent: AbstractContainerizedAgent(name="inventory-agent-${Random.nextInt()}") {

    private val productMap = mutableMapOf<Int,Int>()


    override fun preStart() {

        productMap.put(0, 10);
        productMap.put(1, 20);
        productMap.put(2, 30);

        super.preStart()
    }

    override fun behaviour() = super.behaviour().and(act {

        listen<Message>("inventory-channel") {
            // listen to order message, and respond with the product availability
            val order = RestHelper.mapper.convertValue(it.payload, OrderMessage::class.java)
            log.info("Received order message: $order")
        }

    })


}
