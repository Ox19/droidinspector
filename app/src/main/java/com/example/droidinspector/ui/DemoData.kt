package com.example.droidinspector.ui

import com.example.droidinspector.core.AppUsage
import com.example.droidinspector.core.AuditDetail
import com.example.droidinspector.core.AuditRange
import com.example.droidinspector.core.AuditReport
import com.example.droidinspector.core.PackageEvent
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Datos de muestra para la pantalla de demostración. **No son una medición**: son valores
 * editables para enseñar cómo se vería el reporte. La auditoría real nunca pasa por aquí.
 * Ver el README, sección "Modo demostración".
 */
object DemoData {

    // --- Parámetros editables ---------------------------------------------------------------
    private const val BUMBLE = "com.bumble.app"
    private const val BUMBLE_USAGE_MINUTES = 73L

    /** Actividad de Bumble que se quiere mostrar: fecha, hora y tipo. */
    private val BUMBLE_ACTIVITY = listOf(
        Activity(2025, 11, 7, 22, 47, PackageEvent.Type.INSTALL),
        Activity(2025, 11, 9, 3, 38, PackageEvent.Type.UNINSTALL),
        Activity(2025, 12, 16, 16, 35, PackageEvent.Type.INSTALL),
        Activity(2025, 12, 16, 21, 19, PackageEvent.Type.UNINSTALL),
    )

    /** Otras apps de ejemplo con minutos de uso, para que el resumen no quede vacío. */
    private val SAMPLE_USAGE = listOf(
        BUMBLE to BUMBLE_USAGE_MINUTES,
        "com.whatsapp" to 128L,
        "com.google.android.youtube" to 54L,
        "com.instagram.android" to 41L,
    )
    // ----------------------------------------------------------------------------------------

    private data class Activity(
        val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int,
        val type: PackageEvent.Type,
    )

    fun report(zone: ZoneId): AuditReport {
        val now = System.currentTimeMillis()
        val appUsages = SAMPLE_USAGE.map { (pkg, minutes) ->
            AppUsage(packageName = pkg, totalMillis = minutes * 60_000L, lastUseMillis = now)
        }.sortedByDescending { it.totalMillis }

        val events = BUMBLE_ACTIVITY.map { a ->
            val millis = LocalDateTime.of(a.year, a.month, a.day, a.hour, a.minute)
                .atZone(zone).toInstant().toEpochMilli()
            PackageEvent(BUMBLE, millis, a.type)
        }.sortedBy { it.timestampMillis }

        return AuditReport(
            range = AuditRange(now - 30L * 24 * 60 * 60 * 1000, now),
            detail = AuditDetail.Detailed(hourlyTotals = emptyList(), longestSessions = emptyList(), perApp = emptyMap()),
            totalUsageMillis = appUsages.sumOf { it.totalMillis },
            appUsages = appUsages,
            dailyTotals = emptyList(),
            installEvents = events,
        )
    }

    fun packages(): List<String> = SAMPLE_USAGE.map { it.first } + BUMBLE
}
