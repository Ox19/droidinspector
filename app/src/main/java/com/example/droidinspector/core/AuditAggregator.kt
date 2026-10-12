package com.example.droidinspector.core

import com.example.droidinspector.shared.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Agrega las sesiones en las métricas del reporte. La zona horaria se recibe en el
 * constructor para que el reparto por día y por hora sea reproducible en los tests.
 */
class AuditAggregator(private val zone: ZoneId) {

    fun aggregate(sessions: List<AppSession>, range: AuditRange): AuditReport {
        val hourlyByHour = distribute(sessions, ::hourAt, ::startOfNextHour)

        return AuditReport(
            range = range,
            totalUsageMillis = sessions.sumOf { it.durationMillis },
            appUsages = appUsages(sessions),
            dailyTotals = distribute(sessions, ::dateAt, ::startOfNextDay)
                .map { (date, millis) -> DailyTotal(date, millis) }
                .sortedBy { it.date },
            hourlyTotals = (0..23).map { HourlyTotal(it, hourlyByHour[it] ?: 0L) },
            longestSessions = sessions
                .sortedByDescending { it.durationMillis }
                .take(Config.LONGEST_SESSIONS_LIMIT),
        )
    }

    private fun appUsages(sessions: List<AppSession>): List<AppUsage> =
        sessions.groupBy { it.packageName }
            .map { (packageName, appSessions) ->
                AppUsage(
                    packageName = packageName,
                    totalMillis = appSessions.sumOf { it.durationMillis },
                    launchCount = appSessions.size,
                    firstUseMillis = appSessions.minOf { it.startMillis },
                    lastUseMillis = appSessions.maxOf { it.endMillis },
                )
            }
            .sortedWith(compareByDescending<AppUsage> { it.totalMillis }.thenBy { it.packageName })

    /**
     * Reparte cada sesión entre los tramos que cruza: una que pasa la medianoche suma
     * en los dos días, y una de 90 minutos suma en dos horas distintas.
     */
    private fun <K> distribute(
        sessions: List<AppSession>,
        keyAt: (Long) -> K,
        nextBoundaryAt: (Long) -> Long,
    ): Map<K, Long> {
        val totals = mutableMapOf<K, Long>()
        for (session in sessions) {
            var cursor = session.startMillis
            while (cursor < session.endMillis) {
                val boundary = minOf(nextBoundaryAt(cursor), session.endMillis)
                // Guarda: un límite que no avanza colgaría la app
                if (boundary <= cursor) break
                val key = keyAt(cursor)
                totals[key] = (totals[key] ?: 0L) + (boundary - cursor)
                cursor = boundary
            }
        }
        return totals
    }

    private fun dateAt(millis: Long): LocalDate = zonedAt(millis).toLocalDate()

    private fun hourAt(millis: Long): Int = zonedAt(millis).hour

    private fun startOfNextDay(millis: Long): Long =
        zonedAt(millis).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun startOfNextHour(millis: Long): Long =
        zonedAt(millis).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()

    private fun zonedAt(millis: Long): ZonedDateTime =
        Instant.ofEpochMilli(millis).atZone(zone)
}
