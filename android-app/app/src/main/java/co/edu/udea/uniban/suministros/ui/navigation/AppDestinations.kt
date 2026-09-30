package co.edu.udea.uniban.suministros.ui.navigation

object AppDestinations {
    const val INVENTORY = "inventory"
    const val DETAIL = "inventory/{inventoryId}"
    const val ENTRY = "entry?inventoryId={inventoryId}"

    fun detail(id: String) = "inventory/$id"
    fun entry(id: String = "") = "entry?inventoryId=$id"
}
