package com.example.droidinspector.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los agregados del sistema llegan duplicados y a veces corruptos: eso se prueba aquí. */
class BucketAggregatorTest {

    private val aggregator = BucketAggregator(ZONE)
    private val requested = AuditRange(at(10, 1), at(10, 20))

    @Test
    fun `adds up the buckets of the same app`() {
        val report = aggregator.aggregate(
            listOf(
                bucket("chat", at(10, 1), at(10, 2), 30 * MINUTE),
                bucket("chat", at(10, 2), at(10, 3), 45 * MINUTE),
            ),
            requested,
            AuditBucket.DAILY,
        )

        assertEquals(75 * MINUTE, report.appUsages.single().totalMillis)
        assertEquals(75 * MINUTE, report.totalUsageMillis)
    }

    @Test
    fun `counts a repeated bucket once`() {
        // El sistema devuelve el mismo bucket desde memoria y desde disco
        val report = aggregator.aggregate(
            listOf(
                bucket("chat", at(10, 1), at(10, 2), 30 * MINUTE),
                bucket("chat", at(10, 1), at(10, 2), 30 * MINUTE),
            ),
            requested,
            AuditBucket.DAILY,
        )

        assertEquals(30 * MINUTE, report.totalUsageMillis)
    }

    @Test
    fun `keeps the largest value of a repeated bucket`() {
        val report = aggregator.aggregate(
            listOf(
                bucket("chat", at(10, 1), at(10, 2), 30 * MINUTE),
                bucket("chat", at(10, 1), at(10, 2), 50 * MINUTE),
            ),
            requested,
            AuditBucket.DAILY,
        )

        assertEquals(50 * MINUTE, report.totalUsageMillis)
    }

    @Test
    fun `drops buckets with impossible numbers`() {
        val report = aggregator.aggregate(
            listOf(
                bucket("ok", at(10, 1), at(10, 2), 30 * MINUTE),
                bucket("zero", at(10, 1), at(10, 2), 0L),
                bucket("negative", at(10, 1), at(10, 2), -5L),
                bucket("inverted", at(10, 3), at(10, 2), 10 * MINUTE),
                // Más uso que la duración del propio bucket
                bucket("impossible", at(10, 1), at(10, 2), 48 * 60 * MINUTE),
                bucket("", at(10, 1), at(10, 2), 10 * MINUTE),
            ),
            requested,
            AuditBucket.DAILY,
        )

        assertEquals(listOf("ok"), report.appUsages.map { it.packageName })
    }

    @Test
    fun `the effective range comes from the buckets, not from the request`() {
        // Pedir media semana devuelve meses enteros: se declara en vez de recortar
        val report = aggregator.aggregate(
            listOf(
                bucket("chat", at(9, 1), at(10, 1), 2 * 60 * MINUTE),
                bucket("chat", at(10, 1), at(11, 1), 3 * 60 * MINUTE),
            ),
            AuditRange(at(9, 15), at(10, 20)),
            AuditBucket.MONTHLY,
        )

        val detail = report.detail as AuditDetail.Summarized
        assertEquals(at(9, 1), detail.effectiveRange.startMillis)
        assertEquals(at(11, 1), detail.effectiveRange.endMillis)
        assertEquals(AuditBucket.MONTHLY, detail.bucket)
    }

    @Test
    fun `falls back to the requested range when nothing is usable`() {
        val report = aggregator.aggregate(emptyList(), requested, AuditBucket.MONTHLY)

        val detail = report.detail as AuditDetail.Summarized
        assertEquals(requested, detail.effectiveRange)
        assertEquals(0L, report.totalUsageMillis)
        assertTrue(report.appUsages.isEmpty())
    }

    @Test
    fun `keeps the most recent use of each app`() {
        val report = aggregator.aggregate(
            listOf(
                bucket("chat", at(10, 1), at(10, 2), 30 * MINUTE, lastUsed = at(10, 2)),
                bucket("chat", at(10, 5), at(10, 6), 10 * MINUTE, lastUsed = at(10, 6)),
            ),
            requested,
            AuditBucket.DAILY,
        )

        assertEquals(at(10, 6), report.appUsages.single().lastUseMillis)
    }

    @Test
    fun `daily buckets give real per-day totals, not estimated`() {
        val daily = aggregator.aggregate(
            listOf(bucket("chat", at(10, 1), at(10, 2), 30 * MINUTE)),
            requested,
            AuditBucket.DAILY,
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 1)), daily.dailyTotals.map { it.date })
        assertTrue("el detalle diario real no se marca estimado", daily.dailyTotals.none { it.estimated })
    }

    @Test
    fun `monthly buckets spread the total across their days, marked estimated`() {
        // Un bucket de 4 días con 40 min: el detalle diario no existe, se reparte 10 min/día
        val monthly = aggregator.aggregate(
            listOf(bucket("chat", at(10, 1), at(10, 5), 40 * MINUTE)),
            requested,
            AuditBucket.MONTHLY,
        )

        assertEquals(4, monthly.dailyTotals.size)
        assertTrue("el reparto va marcado como estimación", monthly.dailyTotals.all { it.estimated })
        assertEquals(10 * MINUTE, monthly.dailyTotals.first().totalMillis)
        assertTrue("estimado no inventa franja horaria", monthly.dailyTotals.all { it.firstActivityMillis == null })
    }

    @Test
    fun `sorts apps by time and the total matches the sum`() {
        val report = aggregator.aggregate(
            listOf(
                bucket("chat", at(10, 1), at(10, 2), 30 * MINUTE),
                bucket("maps", at(10, 1), at(10, 2), 90 * MINUTE),
            ),
            requested,
            AuditBucket.DAILY,
        )

        assertEquals(listOf("maps", "chat"), report.appUsages.map { it.packageName })
        assertEquals(report.totalUsageMillis, report.appUsages.sumOf { it.totalMillis })
    }

    private companion object {
        const val MINUTE = 60_000L
        val ZONE: ZoneId = ZoneId.of("America/Lima")

        fun at(month: Int, day: Int): Long =
            ZonedDateTime.of(2026, month, day, 0, 0, 0, 0, ZONE).toInstant().toEpochMilli()

        fun bucket(
            packageName: String,
            start: Long,
            end: Long,
            foreground: Long,
            lastUsed: Long = start,
        ) = UsageBucket(packageName, start, end, foreground, lastUsed)
    }
}
