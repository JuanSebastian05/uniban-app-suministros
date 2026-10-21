package co.edu.udea.uniban.suministros.data.local

/** Tipos de movimiento de inventario definidos en el backlog (HU_04, HU_05). */
object MovementType {
    const val ENTRADA = "ENTRADA"
    const val SALIDA = "SALIDA"
    const val CONSUMO = "CONSUMO"
    const val PERDIDA = "PERDIDA"
    const val AJUSTE = "AJUSTE"

    /** Tipos que hoy se pueden registrar desde la app (HU_04 y HU_05). SALIDA y AJUSTE aún no. */
    val REGISTRABLE = listOf(ENTRADA, CONSUMO, PERDIDA)

    /** RT_10: entradas y ajustes suman; salidas, consumos y pérdidas restan. */
    fun subtracts(type: String) = type == SALIDA || type == CONSUMO || type == PERDIDA

    fun label(type: String) = when (type) {
        ENTRADA -> "Entrada"
        SALIDA -> "Salida"
        CONSUMO -> "Consumo"
        PERDIDA -> "Pérdida"
        AJUSTE -> "Ajuste"
        else -> type
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
