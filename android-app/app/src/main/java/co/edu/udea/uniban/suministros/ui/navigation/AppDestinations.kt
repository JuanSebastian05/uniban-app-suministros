package co.edu.udea.uniban.suministros.ui.navigation

object AppDestinations {
    const val LOGIN = "login"
    const val REGISTER = "register"

    /** Cuenta de Firebase sin perfil en el backend: completar los datos antes de usar la app (P-02). */
    const val COMPLETE_PROFILE = "complete-profile"

    /** Grafo anidado con las pantallas internas; solo accesible con sesión. */
    const val MAIN = "main"
    const val INVENTORY = "inventory"
    const val SYNC_STATUS = "sync-status"
    const val DETAIL = "inventory/{inventoryId}"
    const val ENTRY = "entry?inventoryId={inventoryId}"

    /** Parcelas (HU_14). */
    const val PARCELS = "parcels"
    const val PARCEL_NEW = "parcels/new"

    /** Actividades productivas (HU_08); `parcelId` vacío = el formulario deja elegir la parcela. */
    const val ACTIVITIES = "activities"
    const val ACTIVITY_NEW = "activities/new?parcelId={parcelId}"

    fun activityNew(parcelId: String = "") = "activities/new?parcelId=$parcelId"

    /** Detalle de parcela (HU_02). Prefijo distinto de `parcels/` para no chocar con `parcels/new`. */
    const val PARCEL_DETAIL = "parcel/{parcelId}"

    /** Historial de movimientos (HU_07); `inventoryId` vacío = todos los insumos. */
    const val HISTORY = "history?inventoryId={inventoryId}"

    fun history(inventoryId: String = "") = "history?inventoryId=$inventoryId"

    fun parcelDetail(id: String) = "parcel/$id"

    fun detail(id: String) = "inventory/$id"
    fun entry(id: String = "") = "entry?inventoryId=$id"
}
