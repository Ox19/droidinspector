package com.example.droidinspector.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Agrega los agregados que ya calculó el sistema, para periodos que exceden la retención
 * de los eventos finos.
 *
 * Dos cuidados que no son opcionales: el sistema devuelve el mismo bucket repetido (copia en
 * memoria y copia en disco), así que sumar a ciegas infla los totales; y algunos fabricantes
 * devuelven buckets con cifras imposibles, que se descartan en vez de propagarlas.
 */
class BucketAggregator(private val zone: ZoneId) {

    fun aggregate(
        buckets: List<UsageBucket>,
        requestedRange: AuditRange,
        bucket: AuditBucket,
    ): AuditReport {
        val usable = deduplicate(buckets.filter(::isUsable))

        return AuditReport(
            range = requestedRange,
            detail = AuditDetail.Summarized(
                bucket = bucket,
                effectiveRange = effectiveRange(usable, requestedRange),
            ),
            totalUsageMillis = usable.sumOf { it.foregroundMillis },
            appUsages = appUsages(usable),
            // Solo el bucket diario permite una línea por día sin inventar precisión
            // Diario: real. Periodos más anchos: se reparte el total entre sus días (estimado)
            dailyTotals = if (bucket == AuditBucket.DAILY) dailyTotals(usable) else proratedDaily(usable),
        )
    }

    private fun isUsable(bucket: UsageBucket): Boolean =
        bucket.packageName.isNotEmpty() &&
            bucket.foregroundMillis > 0 &&
            bucket.widthMillis > 0 &&
            // Más uso que la duración del propio bucket es imposible
            bucket.foregroundMillis <= bucket.widthMillis

    /** El mismo bucket puede llegar repetido: se conserva el mayor, nunca la suma. */
    private fun deduplicate(buckets: List<UsageBucket>): List<UsageBucket> =
        buckets.groupBy { it.packageName to it.startMillis }
            .values
            .map { repeated -> repeated.maxBy { it.foregroundMillis } }

    private fun appUsages(buckets: List<UsageBucket>): List<AppUsage> =
        buckets.groupBy { it.packageName }
            .map { (packageName, appBuckets) ->
                AppUsage(
                    packageName = packageName,
                    totalMillis = appBuckets.sumOf { it.foregroundMillis },
                    lastUseMillis = appBuckets.maxOf { it.lastUsedMillis },
                )
            }
            .sortedWith(compareByDescending<AppUsage> { it.totalMillis }.thenBy { it.packageName })

    /**
     * Lo que cubren los buckets, que puede desbordar el rango pedido porque el sistema
     * entrega el total del bucket completo. Se declara en vez de prorratearlo.
     */
    private fun effectiveRange(buckets: List<UsageBucket>, requested: AuditRange): AuditRange =
        if (buckets.isEmpty()) {
            requested
        } else {
            AuditRange(
                startMillis = buckets.minOf { it.startMillis },
                endMillis = buckets.maxOf { it.endMillis },
            )
        }

    private fun dailyTotals(buckets: List<UsageBucket>): List<DailyTotal> =
        buckets.groupBy { dateAt(it.startMillis) }
            .map { (date, dayBuckets) -> DailyTotal(date, dayBuckets.sumOf { it.foregroundMillis }) }
            .sortedBy { it.date }

    /**
     * El sistema no guarda el detalle diario de un mes, así que el total de cada bucket se
     * reparte a partes iguales entre los días que cubre. Es una estimación y va marcada como tal.
     */
    private fun proratedDaily(buckets: List<UsageBucket>): List<DailyTotal> {
        val totals = mutableMapOf<LocalDate, Long>()
        for (bucket in buckets) {
            val days = daysOf(bucket.startMillis, bucket.endMillis)
            if (days.isEmpty()) continue
            val perDay = bucket.foregroundMillis / days.size
            days.forEach { date -> totals[date] = (totals[date] ?: 0L) + perDay }
        }
        return totals.map { (date, millis) -> DailyTotal(date, millis, estimated = true) }
            .sortedBy { it.date }
    }

    private fun daysOf(startMillis: Long, endMillis: Long): List<LocalDate> {
        val first = dateAt(startMillis)
        val last = dateAt(endMillis - 1)
        if (last.isBefore(first)) return emptyList()
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .toList()
    }

    private fun dateAt(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
