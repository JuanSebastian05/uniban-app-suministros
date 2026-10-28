package co.edu.udea.uniban.suministros.data.local

import co.edu.udea.uniban.suministros.data.CycleStatus

/** Tipos de actividad productiva (HU_08), los de los mockups. [name] es el valor que se guarda y viaja al backend. */
enum class ActivityType(val label: String) {
    FERTILIZACION("Fertilización"),
    SIEMBRA("Siembra"),
    COSECHA("Cosecha"),
    APLICACION_INSUMO("Aplicación de insumo"),
    CONTROL_FITOSANITARIO("Control fitosanitario"),
    OTRA("Otra actividad"),
    ;

    companion object {
        fun fromCode(code: String?): ActivityType? = entries.firstOrNull { it.name == code }
    }

    /** Explica las reglas de las actividades con etapa obligatoria. El repositorio también las valida. */
    fun stageWarning(currentStatus: String?): String? = when {
        this == COSECHA && currentStatus == CycleStatus.EN_PREPARACION.name ->
            "La cosecha requiere Producción y se registra con cantidad, aforamiento y recomendación."
        this == SIEMBRA && currentStatus == CycleStatus.PRODUCCION.name ->
            "La siembra solo se permite durante En preparación."
        this == SIEMBRA && currentStatus == CycleStatus.DESCANSO.name ->
            "La siembra requiere iniciar un ciclo en En preparación. No se permite durante Descanso."
        this == COSECHA && currentStatus == CycleStatus.DESCANSO.name ->
            "La cosecha solo se permite durante Producción. Durante Descanso puedes despachar racimos ya cosechados."
        else -> null
    }
}
