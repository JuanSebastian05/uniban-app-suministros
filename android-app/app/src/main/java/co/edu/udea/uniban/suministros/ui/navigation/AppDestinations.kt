package co.edu.udea.uniban.suministros.ui.navigation

object AppDestinations {
    const val LOGIN = "login"
    const val REGISTER = "register"

    /** Grafo anidado con las pantallas internas; solo accesible con sesión. */
    const val MAIN = "main"
    const val INVENTORY = "inventory"
    const val DETAIL = "inventory/{inventoryId}"
    const val ENTRY = "entry?inventoryId={inventoryId}"

    /** Parcelas (HU_14). */
    const val PARCELS = "parcels"
    const val PARCEL_NEW = "parcels/new"

    fun detail(id: String) = "inventory/$id"
    fun entry(id: String = "") = "entry?inventoryId=$id"
}
