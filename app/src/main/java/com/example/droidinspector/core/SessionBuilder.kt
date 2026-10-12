package com.example.droidinspector.core

/**
 * Convierte eventos sueltos en sesiones con duración, recortadas al rango.
 *
 * El sistema no garantiza eventos bien formados: una app puede quedarse sin su evento
 * de salida (la cierra la pantalla apagada o el final del rango) y dos pueden
 * solaparse en multiventana, así que se lleva una sesión abierta por paquete.
 */
class SessionBuilder {

    fun build(events: List<UsageEvent>, range: AuditRange): List<AppSession> {
        val openStarts = mutableMapOf<String, Long>()
        val sessions = mutableListOf<AppSession>()

        for (event in events.sortedBy { it.timestampMillis }) {
            when (event.type) {
                // Un segundo FOREGROUND sin salida previa no abre otra sesión
                UsageEvent.Type.FOREGROUND ->
                    if (!openStarts.containsKey(event.packageName)) {
                        openStarts[event.packageName] = event.timestampMillis
                    }

                UsageEvent.Type.BACKGROUND ->
                    openStarts.remove(event.packageName)?.let { start ->
                        sessions += AppSession(event.packageName, start, event.timestampMillis)
                    }

                UsageEvent.Type.SESSION_BREAK -> {
                    openStarts.forEach { (packageName, start) ->
                        sessions += AppSession(packageName, start, event.timestampMillis)
                    }
                    openStarts.clear()
                }
            }
        }

        // Lo que siga abierto al acabar los eventos se cierra con el rango
        openStarts.forEach { (packageName, start) ->
            sessions += AppSession(packageName, start, range.endMillis)
        }

        return sessions.mapNotNull { it.clampedTo(range) }.sortedBy { it.startMillis }
    }

    private fun AppSession.clampedTo(range: AuditRange): AppSession? {
        val start = maxOf(startMillis, range.startMillis)
        val end = minOf(endMillis, range.endMillis)
        return if (end > start) copy(startMillis = start, endMillis = end) else null
    }
}
