package com.example.droidinspector.core

import java.time.LocalDate

/** Rango auditado, en epoch millis; `end` es exclusivo. */
data class AuditRange(val startMillis: Long, val endMillis: Long)

/**
 * Evento de uso ya traducido desde el sistema: `core` no conoce `android.*`.
 * `SESSION_BREAK` cubre pantalla apagada o apagado del equipo, que cierran todo.
 */
data class UsageEvent(
    val packageName: String,
    val timestampMillis: Long,
    val type: Type,
) {
    enum class Type { FOREGROUND, BACKGROUND, SESSION_BREAK }
}

/** Uso continuo de una app; `end` es exclusivo. */
data class AppSession(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long get() = endMillis - startMillis
}

/** Totales de una app en el rango. */
data class AppUsage(
    val packageName: String,
    val totalMillis: Long,
    val launchCount: Int,
    val firstUseMillis: Long,
    val lastUseMillis: Long,
)

data class DailyTotal(val date: LocalDate, val totalMillis: Long)

data class HourlyTotal(val hour: Int, val totalMillis: Long)

/** Resultado de la auditoría. Usa nombres de paquete: la `ui` resuelve el nombre visible. */
data class AuditReport(
    val range: AuditRange,
    val totalUsageMillis: Long,
    val appUsages: List<AppUsage>,
    val dailyTotals: List<DailyTotal>,
    val hourlyTotals: List<HourlyTotal>,
    val longestSessions: List<AppSession>,
)
