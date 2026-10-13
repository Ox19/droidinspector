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
                UsageEvent.Type.FOREGROUND -> {
                    // Solo una app tiene el foco: la entrada de otra cierra lo que quedó
                    // abierto, aunque su evento de salida nunca llegara
                    closeOthers(event.packageName, event.timestampMillis, openStarts, sessions)
                    // Un segundo FOREGROUND de la misma app no abre otra sesión
                    if (!openStarts.containsKey(event.packageName)) {
                        openStarts[event.packageName] = event.timestampMillis
                    }
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

    private fun closeOthers(
        packageName: String,
        millis: Long,
        openStarts: MutableMap<String, Long>,
        sessions: MutableList<AppSession>,
    ) {
        val others = openStarts.filterKeys { it != packageName }
        others.forEach { (otherPackage, start) ->
            sessions += AppSession(otherPackage, start, millis)
            openStarts.remove(otherPackage)
        }
    }

    private fun AppSession.clampedTo(range: AuditRange): AppSession? {
        val start = maxOf(startMillis, range.startMillis)
        val end = minOf(endMillis, range.endMillis)
        return if (end > start) copy(startMillis = start, endMillis = end) else null
    }
}
