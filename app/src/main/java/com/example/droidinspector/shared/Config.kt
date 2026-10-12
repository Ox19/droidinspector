package com.example.droidinspector.shared

/** Valores fijos del proyecto. Sin lógica. */
object Config {
    /** El sistema solo retiene eventos finos unos días; más atrás no hay datos fiables. */
    const val EVENT_RETENTION_DAYS = 7L

    /** Cuántas sesiones largas se listan en el reporte. */
    const val LONGEST_SESSIONS_LIMIT = 5
}
