package com.example.droidinspector.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Formatos de presentación; viven en `ui` porque `core` no decide cómo se muestra. */
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}min"
        minutes > 0 -> "${minutes}min"
        else -> "<1min"
    }
}

fun formatDay(millis: Long, zone: ZoneId): String =
    DATE_FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone))
