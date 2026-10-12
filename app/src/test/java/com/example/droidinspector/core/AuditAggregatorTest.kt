package com.example.droidinspector.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** El reparto por día y por hora debe cuadrar aunque la sesión cruce los límites. */
class AuditAggregatorTest {

    private val aggregator = AuditAggregator(ZONE)
    private val range = AuditRange(at(10, 0), at(13, 0))

    @Test
    fun `sums time and counts launches per app`() {
        val report = aggregator.aggregate(
            listOf(
                session("chat", at(10, 9), at(10, 10)),
                session("chat", at(10, 11), at(10, 11, 30)),
                session("maps", at(10, 12), at(10, 14)),
            ),
            range,
        )

        assertEquals((60 + 30 + 120) * MINUTE, report.totalUsageMillis)
        assertEquals(listOf("maps", "chat"), report.appUsages.map { it.packageName })
        val chat = report.appUsages.single { it.packageName == "chat" }
        assertEquals(2, chat.launchCount)
        assertEquals(90 * MINUTE, chat.totalMillis)
        assertEquals(at(10, 9), chat.firstUseMillis)
        assertEquals(at(10, 11, 30), chat.lastUseMillis)
    }

    @Test
    fun `splits a session crossing midnight across both days`() {
        val report = aggregator.aggregate(
            listOf(session("chat", at(10, 23), at(11, 1))),
            range,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)),
            report.dailyTotals.map { it.date },
        )
        assertEquals(60 * MINUTE, report.dailyTotals[0].totalMillis)
        assertEquals(60 * MINUTE, report.dailyTotals[1].totalMillis)
    }

    @Test
    fun `splits a session across the hours it spans`() {
        val report = aggregator.aggregate(
            listOf(session("chat", at(10, 9, 30), at(10, 11))),
            range,
        )

        assertEquals(30 * MINUTE, report.hourlyTotals[9].totalMillis)
        assertEquals(60 * MINUTE, report.hourlyTotals[10].totalMillis)
        assertEquals(0L, report.hourlyTotals[11].totalMillis)
    }

    @Test
    fun `hourly totals always cover the full day`() {
        val report = aggregator.aggregate(emptyList(), range)

        assertEquals(24, report.hourlyTotals.size)
        assertEquals((0..23).toList(), report.hourlyTotals.map { it.hour })
        assertEquals(0L, report.totalUsageMillis)
    }

    @Test
    fun `lists the longest sessions first and caps the list`() {
        val sessions = (1..8).map { session("app$it", at(10, 1), at(10, 1, it)) }

        val report = aggregator.aggregate(sessions, range)

        assertEquals(5, report.longestSessions.size)
        assertEquals("app8", report.longestSessions.first().packageName)
    }

    @Test
    fun `daily totals add up to the overall total`() {
        val sessions = listOf(
            session("chat", at(10, 22), at(11, 2)),
            session("maps", at(12, 10), at(12, 11)),
        )

        val report = aggregator.aggregate(sessions, range)

        assertEquals(report.totalUsageMillis, report.dailyTotals.sumOf { it.totalMillis })
        assertEquals(report.totalUsageMillis, report.hourlyTotals.sumOf { it.totalMillis })
    }

    private companion object {
        const val MINUTE = 60_000L
        val ZONE: ZoneId = ZoneId.of("America/Lima")

        fun at(day: Int, hour: Int, minute: Int = 0): Long =
            ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli()

        fun session(packageName: String, start: Long, end: Long) =
            AppSession(packageName, start, end)
    }
}
