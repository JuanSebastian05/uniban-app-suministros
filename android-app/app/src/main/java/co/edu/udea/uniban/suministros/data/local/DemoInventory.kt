package co.edu.udea.uniban.suministros.data.local

import java.util.UUID

internal object DemoInventory {
    private fun id(value: String) = UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    val producer = ProducerEntity(id("demo-producer"), "Productor de demostración")
    val location = LocationEntity(id("demo-location"), producer.id, "Inventario principal")
    private val entries = listOf(
        SupplyEntity(id("fertilizante"), "Fertilizante 15-15-15", "Fertilizante", "kg") to 120f,
        SupplyEntity(id("fungicida"), "Fungicida Mancozeb", "Fungicida", "L") to 35f,
        SupplyEntity(id("urea"), "Urea 46%", "Fertilizante", "kg") to 80f,
        SupplyEntity(id("insecticida"), "Clorpirifos 480 EC", "Insecticida", "L") to 12f,
        SupplyEntity(id("cal"), "Cal dolomítica", "Correctivo", "kg") to 200f,
        SupplyEntity(id("sulfato"), "Sulfato de potasio", "Fertilizante", "kg") to 60f,
    )
    val supplies = entries.map { it.first }
    val inventory = entries.map { (supply, quantity) ->
        InventoryEntity(id("inventory-${supply.id}"), location.id, supply.id, quantity, quantity)
    }
}

