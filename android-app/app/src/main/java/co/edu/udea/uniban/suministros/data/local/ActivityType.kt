package co.edu.udea.uniban.suministros.data.local

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
}
