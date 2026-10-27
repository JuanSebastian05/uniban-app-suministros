package co.edu.udea.uniban.suministros.data.local

import java.util.UUID

/**
 * Inventario inicial de una cuenta. Los ids se derivan del uid: cada usuario tiene su propia
 * ubicación, catálogo e inventario, y el backend nunca mezcla los de dos cuentas.
 */
internal class StarterInventory(private val ownerUid: String) {
    private fun id(value: String) = UUID.nameUUIDFromBytes("$ownerUid/$value".toByteArray(Charsets.UTF_8)).toString()

    val producer = ProducerEntity(id("producer"), "Mi inventario")
    val location = LocationEntity(id("location"), producer.id, "Inventario principal")
    private val agriculturalEntries = listOf(
        SupplyEntity(id("fertilizante"), "Fertilizante 15-15-15", "Fertilizante", "kg") to 120f,
        SupplyEntity(id("fungicida"), "Fungicida Mancozeb", "Fungicida", "L") to 35f,
        SupplyEntity(id("urea"), "Urea 46%", "Fertilizante", "kg") to 80f,
        SupplyEntity(id("insecticida"), "Clorpirifos 480 EC", "Insecticida", "L") to 12f,
        SupplyEntity(id("cal"), "Cal dolomítica", "Correctivo", "kg") to 200f,
        SupplyEntity(id("sulfato"), "Sulfato de potasio", "Fertilizante", "kg") to 60f,
    )
    private val packagingEntries = listOf("cajas", "carton", "bolsas").map { name ->
        SupplyEntity(id(name), name, "Empaque", "unidad") to 0f
    }
    private val entries = agriculturalEntries + packagingEntries
    val supplies = entries.map { it.first }
    val inventory = entries.map { (supply, quantity) ->
        InventoryEntity(id("inventory-${supply.id}"), location.id, supply.id, quantity, quantity)
    }
    /** Materiales que también se incorporan a las cuentas creadas antes de ampliar el catálogo. */
    val packagingSupplies = packagingEntries.map { it.first }
    val packagingInventory = inventory.filter { item -> packagingSupplies.any { it.id == item.supplyId } }

    companion object {
        /** Dueño de la sesión de demostración (debug sin Firebase); sus datos nunca se sincronizan. */
        const val DEMO_OWNER = "demo"
    }
}
