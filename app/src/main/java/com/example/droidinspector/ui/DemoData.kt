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
 *
 * La actividad de instalación/desinstalación de la app objetivo (Bumble) solo se incluye si
 * esa app está instalada en el dispositivo; si no, el reporte de muestra es el normal sin ella.
 */
object DemoData {

    // --- Parámetros editables ---------------------------------------------------------------
    const val TARGET_PACKAGE = "com.bumble.app"
    private const val TARGET_USAGE_MINUTES = 73L

    /** Actividad de la app objetivo: fecha, hora y tipo. Va solo al reporte (PDF). */
    private val TARGET_ACTIVITY = listOf(
        Activity(2025, 11, 7, 22, 47, PackageEvent.Type.INSTALL),
        Activity(2025, 11, 9, 3, 38, PackageEvent.Type.UNINSTALL),
        Activity(2025, 12, 16, 16, 35, PackageEvent.Type.INSTALL),
        Activity(2025, 12, 16, 21, 19, PackageEvent.Type.UNINSTALL),
    )

    /** Apps de ejemplo (siempre presentes) con minutos de uso. */
    private val BASE_USAGE = listOf(
        "com.whatsapp" to 128L,
        "com.google.android.youtube" to 54L,
        "com.instagram.android" to 41L,
    )
    // ----------------------------------------------------------------------------------------

    private data class Activity(
        val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int,
        val type: PackageEvent.Type,
    )

    /** `targetInstalled` = si la app objetivo está en el dispositivo (lo decide el ViewModel). */
    fun report(zone: ZoneId, targetInstalled: Boolean): AuditReport {
        val now = System.currentTimeMillis()

        val usage = buildList {
            addAll(BASE_USAGE)
            if (targetInstalled) add(TARGET_PACKAGE to TARGET_USAGE_MINUTES)
        }.map { (pkg, minutes) ->
            AppUsage(pkg, minutes * 60_000L, now)
        }.sortedByDescending { it.totalMillis }

        val events = if (targetInstalled) {
            TARGET_ACTIVITY.map { a ->
                val millis = LocalDateTime.of(a.year, a.month, a.day, a.hour, a.minute)
                    .atZone(zone).toInstant().toEpochMilli()
                PackageEvent(TARGET_PACKAGE, millis, a.type)
            }.sortedBy { it.timestampMillis }
        } else {
            emptyList()
        }

        return AuditReport(
            range = AuditRange(now - 30L * 24 * 60 * 60 * 1000, now),
            detail = AuditDetail.Detailed(emptyList(), emptyList(), emptyMap()),
            totalUsageMillis = usage.sumOf { it.totalMillis },
            appUsages = usage,
            dailyTotals = emptyList(),
            installEvents = events,
        )
    }

    fun packages(): List<String> = BASE_USAGE.map { it.first } + TARGET_PACKAGE
}
