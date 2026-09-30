package co.edu.udea.uniban.suministros.data.local

/** Tipos de movimiento de inventario definidos en el backlog (HU_04, HU_05). */
object MovementType {
    const val ENTRADA = "ENTRADA"
    const val SALIDA = "SALIDA"
    const val CONSUMO = "CONSUMO"
    const val PERDIDA = "PERDIDA"
    const val AJUSTE = "AJUSTE"

    /** RT_10: entradas y ajustes suman; salidas, consumos y pérdidas restan. */
    fun subtracts(type: String) = type == SALIDA || type == CONSUMO || type == PERDIDA
}
