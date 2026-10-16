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

/**
 * Agregado que el sistema conserva por periodo, ya traducido. A diferencia de los eventos,
 * sobrevive meses: es la única vía para auditar periodos largos.
 */
data class UsageBucket(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
    val foregroundMillis: Long,
    val lastUsedMillis: Long,
) {
    val widthMillis: Long get() = endMillis - startMillis
}

/** Anchura del agregado que pide el sistema; `data` lo traduce a sus constantes. */
enum class AuditBucket { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Hasta dónde llega hacia atrás cada fuente **en este dispositivo**. Los fabricantes podan
 * distinto, así que se mide en vez de cablearse. `null` = esa fuente no devolvió nada.
 */
data class RetentionWindows(
    val earliestEventMillis: Long?,
    val earliestByBucket: Map<AuditBucket, Long?>,
) {
    fun earliestFor(bucket: AuditBucket): Long? = earliestByBucket[bucket]
}

/** Totales de una app en el rango; solo lo que los dos modos sostienen de verdad. */
data class AppUsage(
    val packageName: String,
    val totalMillis: Long,
    val lastUseMillis: Long,
)

/**
 * Lo que solo existe con eventos finos. `firstUseMillis` y `launchCount` viven aquí porque
 * en modo resumido el sistema no los da: `firstTimeStamp` es el inicio del bucket, no el
 * primer uso, y el contador de aperturas está retirado desde API 28.
 */
data class AppUsageDetail(
    val launchCount: Int,
    val firstUseMillis: Long,
)

data class DailyTotal(val date: LocalDate, val totalMillis: Long)

data class HourlyTotal(val hour: Int, val totalMillis: Long)

/**
 * Parte del reporte que depende de la fuente. Al ser sellada, el compilador obliga a
 * distinguir los modos y es imposible publicar horarios que no se midieron.
 */
sealed interface AuditDetail {

    /** Eventos finos: hay sesiones, franjas horarias y aperturas. */
    data class Detailed(
        val hourlyTotals: List<HourlyTotal>,
        val longestSessions: List<AppSession>,
        val perApp: Map<String, AppUsageDetail>,
    ) : AuditDetail

    /**
     * Agregados del sistema. `effectiveRange` es lo que los buckets cubren de verdad, que
     * puede ser más ancho que lo pedido: el sistema no recorta los totales al rango.
     */
    data class Summarized(
        val bucket: AuditBucket,
        val effectiveRange: AuditRange,
    ) : AuditDetail
}

/** Resultado de la auditoría. Usa nombres de paquete: la `ui` resuelve el nombre visible. */
data class AuditReport(
    val range: AuditRange,
    val detail: AuditDetail,
    val totalUsageMillis: Long,
    val appUsages: List<AppUsage>,
    val dailyTotals: List<DailyTotal>,
)
