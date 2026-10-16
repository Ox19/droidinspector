package com.example.droidinspector.shared

/** Valores fijos del proyecto. Sin lógica. */
object Config {
    /** El sistema solo retiene eventos finos unos días; más atrás no hay datos fiables. */
    const val EVENT_RETENTION_DAYS = 7L

    /** Hasta dónde mira la sonda para saber qué conserva el dispositivo. */
    const val RETENTION_PROBE_LOOKBACK_DAYS = 1100L
    const val EVENT_PROBE_LOOKBACK_DAYS = 30L

    /** Tope del selector de rango: una auditoría anual con margen de año bisiesto. */
    const val MAX_AUDIT_DAYS = 366L

    /** Cuántas sesiones largas se listan en el reporte. */
    const val LONGEST_SESSIONS_LIMIT = 5

    /**
     * Tope de una sesión que nunca recibió su propio evento de salida.
     *
     * Android reemite un ACTIVITY_RESUMED a medianoche para lo que siguiera en primer
     * plano. Si el equipo estuvo apagado, esa sesión fantasma abarca horas y se cierra
     * con DEVICE_SHUTDOWN: se midieron 12h36 de una app por eso. Solo el PAUSED propio
     * de la app exime del tope; cualquier otro cierre es una suposición.
     *
     * Es una heurística: una sesión real más larga que esto se reporta recortada.
     */
    const val MAX_UNCLOSED_SESSION_MILLIS = 2L * 60L * 60L * 1000L
}
