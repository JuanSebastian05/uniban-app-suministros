package co.edu.udea.uniban.suministros.data.local

/** Proyección de los últimos registros; los consumos de una actividad no se duplican en Inicio. */
data class HomeRecord(
    val id: String,
    val kind: String,
    val type: String,
    val name: String?,
    val quantity: Float?,
    val unit: String?,
    val date: String,
    val createdAt: String,
    val parcelId: String?,
    val inventoryId: String?,
)
