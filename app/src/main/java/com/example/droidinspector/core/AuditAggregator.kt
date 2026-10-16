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

        val byPackage = sessions.groupBy { it.packageName }

        return AuditReport(
            range = range,
            detail = AuditDetail.Detailed(
                hourlyTotals = (0..23).map { HourlyTotal(it, hourlyByHour[it] ?: 0L) },
                longestSessions = sessions
                    .sortedByDescending { it.durationMillis }
                    .take(Config.LONGEST_SESSIONS_LIMIT),
                perApp = byPackage.mapValues { (_, appSessions) ->
                    AppUsageDetail(
                        launchCount = appSessions.size,
                        firstUseMillis = appSessions.minOf { it.startMillis },
                    )
                },
            ),
            totalUsageMillis = sessions.sumOf { it.durationMillis },
            appUsages = appUsages(byPackage),
            dailyTotals = dailyTotals(sessions),
        )
    }

    private fun appUsages(byPackage: Map<String, List<AppSession>>): List<AppUsage> =
        byPackage
            .map { (packageName, appSessions) ->
                AppUsage(
                    packageName = packageName,
                    totalMillis = appSessions.sumOf { it.durationMillis },
                    lastUseMillis = appSessions.maxOf { it.endMillis },
                )
            }
            .sortedWith(compareByDescending<AppUsage> { it.totalMillis }.thenBy { it.packageName })

    /**
     * Reparte cada sesión entre los tramos que cruza: una que pasa la medianoche suma
     * en los dos días, y una de 90 minutos suma en dos horas distintas.
     */
    /** Un día lleva su tiempo total y la franja real: de la primera a la última actividad. */
    private fun dailyTotals(sessions: List<AppSession>): List<DailyTotal> {
        val totals = distribute(sessions, ::dateAt, ::startOfNextDay)
        val spans = activitySpans(sessions)
        val perApp = perAppByDay(sessions)
        return totals.map { (date, millis) ->
            val span = spans[date]
            DailyTotal(
                date = date,
                totalMillis = millis,
                firstActivityMillis = span?.first,
                lastActivityMillis = span?.second,
                perApp = perApp[date].orEmpty(),
            )
        }.sortedBy { it.date }
    }

    /** Por día, cuánto usó cada app (recortado al día), de mayor a menor. */
    private fun perAppByDay(sessions: List<AppSession>): Map<LocalDate, List<DailyAppUsage>> {
        val byDay = mutableMapOf<LocalDate, MutableMap<String, Long>>()
        for (session in sessions) {
            var cursor = session.startMillis
            while (cursor < session.endMillis) {
                val boundary = minOf(startOfNextDay(cursor), session.endMillis)
                if (boundary <= cursor) break
                val dayApps = byDay.getOrPut(dateAt(cursor)) { mutableMapOf() }
                dayApps[session.packageName] = (dayApps[session.packageName] ?: 0L) + (boundary - cursor)
                cursor = boundary
            }
        }
        return byDay.mapValues { (_, apps) ->
            apps.map { (pkg, millis) -> DailyAppUsage(pkg, millis) }
                .sortedWith(compareByDescending<DailyAppUsage> { it.totalMillis }.thenBy { it.packageName })
        }
    }

    /** Por día, el instante más temprano y el más tardío con actividad, recortado al día. */
    private fun activitySpans(sessions: List<AppSession>): Map<LocalDate, Pair<Long, Long>> {
        val spans = mutableMapOf<LocalDate, Pair<Long, Long>>()
        for (session in sessions) {
            var cursor = session.startMillis
            while (cursor < session.endMillis) {
                val boundary = minOf(startOfNextDay(cursor), session.endMillis)
                if (boundary <= cursor) break
                val date = dateAt(cursor)
                val current = spans[date]
                spans[date] = Pair(
                    minOf(current?.first ?: cursor, cursor),
                    maxOf(current?.second ?: boundary, boundary),
                )
                cursor = boundary
            }
        }
        return spans
    }

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
