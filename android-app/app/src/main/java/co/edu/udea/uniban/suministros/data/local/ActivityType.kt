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

    /** Aviso orientativo; nunca impide guardar la actividad. */
    fun stageWarning(currentStatus: String?): String? = when {
        this == COSECHA && currentStatus == CycleStatus.EN_PREPARACION.name ->
            "La etapa actual es En preparación. La cosecha suele registrarse durante Producción; puedes guardarla."
        this == SIEMBRA && currentStatus == CycleStatus.PRODUCCION.name ->
            "La etapa actual es Producción. La siembra suele registrarse durante En preparación; puedes guardarla."
        this == SIEMBRA && currentStatus == CycleStatus.DESCANSO.name ->
            "La parcela está en descanso. Si esta siembra inicia otro cultivo, considera abrir un nuevo ciclo; puedes guardar el registro."
        this == COSECHA && currentStatus == CycleStatus.DESCANSO.name ->
            "La parcela está en descanso. Revisa que la fecha de cosecha sea correcta; puedes guardar el registro."
        else -> null
    }
}
