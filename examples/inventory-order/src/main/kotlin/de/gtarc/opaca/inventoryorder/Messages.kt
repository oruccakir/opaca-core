// import necessary packages
package de.gtarc.opaca.inventoryorder

// I need to define messages for the inventory-order system to communicate with the inventory system, I learned that in order to work with messages in Kotlin, 
//I need to use the `data class` keyword to define a class that only holds data, and I can use the `val` keyword to define properties of the class.

data class InventoryMessage(
    var productId: Int = 0,
    var productName: String = "",
    var productQuantity: Int = 0,
    var productPrice: Double = 0.0,
)

data class OrderMessage(
    var orderId: Int = 0,
    var orderStatus: String = "",
    var orderDate: String = "",
    var orderQuantity: Int = 0,
)