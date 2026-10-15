package com.example.droidinspector.core

import com.example.droidinspector.shared.Config

/**
 * Convierte eventos sueltos en sesiones con duración, recortadas al rango.
 *
 * El sistema no garantiza eventos bien formados: una app puede quedarse sin su evento
 * de salida (la cierra la pantalla apagada o el final del rango) y dos pueden
 * solaparse en multiventana, así que se lleva una sesión abierta por paquete.
 */
class SessionBuilder(
    private val maxUnclosedMillis: Long = Config.MAX_UNCLOSED_SESSION_MILLIS,
) {

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

                // Pantalla o equipo apagados: cierra todo, pero no dice cuándo se dejó
                // de usar de verdad, así que también se acota
                UsageEvent.Type.SESSION_BREAK -> {
                    openStarts.forEach { (packageName, start) ->
                        sessions += unclosedSession(packageName, start, event.timestampMillis)
                    }
                    openStarts.clear()
                }
            }
        }

        // Lo que siga abierto al acabar los eventos se cierra con el rango, acotado
        openStarts.forEach { (packageName, start) ->
            sessions += unclosedSession(packageName, start, range.endMillis)
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
            sessions += unclosedSession(otherPackage, start, millis)
            openStarts.remove(otherPackage)
        }
    }

    /** Sin evento de salida propio no se sabe cuándo acabó: se acota al tope. */
    private fun unclosedSession(packageName: String, start: Long, end: Long) =
        AppSession(packageName, start, minOf(end, start + maxUnclosedMillis))

    private fun AppSession.clampedTo(range: AuditRange): AppSession? {
        val start = maxOf(startMillis, range.startMillis)
        val end = minOf(endMillis, range.endMillis)
        return if (end > start) copy(startMillis = start, endMillis = end) else null
    }
}
