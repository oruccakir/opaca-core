package de.gtarc.opaca.inventoryorder

import de.gtarc.opaca.container.ContainerAgent
import de.gtarc.opaca.util.ConfigLoader
import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import java.lang.IllegalArgumentException

fun main(args: Array<String>) {

    if (args.isEmpty())
        throw IllegalArgumentException("Please provide 'inventory' or 'order' as command line parameter!")

    val imageConfig = when (args[0]) {
        "inventory" -> "/inventory-image.json"
        "order" -> "/order-image.json"
        else -> throw IllegalArgumentException("Argument must be 'inventory' or 'order'!")
    }

    val image = ConfigLoader.loadContainerImageFromResources(imageConfig)
    agentSystem(image.imageName) {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image))
            add(if ("inventory" == args[0]) InventoryAgent() else OrderAgent())
        }
    }.start()
}
