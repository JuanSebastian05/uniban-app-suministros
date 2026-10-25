package co.edu.udea.uniban.suministros.data.local

/** Tipos de movimiento de inventario definidos en el backlog (HU_04, HU_05). */
object MovementType {
    const val ENTRADA = "ENTRADA"
    const val SALIDA = "SALIDA"
    const val CONSUMO = "CONSUMO"
    const val PERDIDA = "PERDIDA"
    const val AJUSTE = "AJUSTE"

    /** Tipos que hoy se pueden registrar desde la app (HU_04, HU_05 y ajustes). SALIDA aún no. */
    val REGISTRABLE = listOf(ENTRADA, CONSUMO, PERDIDA, AJUSTE)

    /**
     * RT_10: entradas suman; salidas, consumos y pérdidas restan. Un ajuste suma o resta según su
     * [direction] ([AdjustDirection.DISMINUCION] resta).
     */
    fun subtracts(type: String, direction: String? = null) =
        type == SALIDA || type == CONSUMO || type == PERDIDA ||
            (type == AJUSTE && direction == AdjustDirection.DISMINUCION.name)

    fun label(type: String) = when (type) {
        ENTRADA -> "Entrada"
        SALIDA -> "Salida"
        CONSUMO -> "Consumo"
        PERDIDA -> "Pérdida"
        AJUSTE -> "Ajuste"
        else -> type
    }
}

/** Sentido de un ajuste (corrección). [name] es el valor que se guarda y viaja al backend. */
enum class AdjustDirection(val label: String, val symbol: String) {
    AUMENTO("Aumentar", "+"),
    DISMINUCION("Disminuir", "−"),
    ;

    companion object {
        fun fromCode(code: String?): AdjustDirection? = entries.firstOrNull { it.name == code }
    }
}

/** Motivos de una pérdida (HU_05). [name] es el valor que se guarda y viaja al backend. */
enum class LossReason(val label: String) {
    DESPERDICIO("Desperdicio"),
    MAL_MANEJO("Mal manejo"),
    CLIMA("Clima"),
    OTRO("Otro"),
    ;

    companion object {
        fun fromCode(code: String?): LossReason? = entries.firstOrNull { it.name == code }
    }
}
