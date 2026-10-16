package com.example.droidinspector.core

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El modo depende de lo que el dispositivo conserva de verdad, no de constantes. */
class AuditPlannerTest {

    private val planner = AuditPlanner()

    @Test
    fun `uses events when the range starts inside their window`() {
        val plan = planner.plan(rangeFrom(day = 12), retention(events = day(10)))

        assertTrue(plan is AuditPlan.Detailed)
    }

    @Test
    fun `uses events exactly at the edge of their window`() {
        val plan = planner.plan(rangeFrom(day = 10), retention(events = day(10)))

        assertTrue(plan is AuditPlan.Detailed)
    }

    @Test
    fun `falls back to the finest bucket that reaches the start`() {
        val plan = planner.plan(
            rangeFrom(day = 5),
            retention(events = day(10), daily = day(3), monthly = day(1)),
        )

        assertEquals(AuditBucket.DAILY, (plan as AuditPlan.Summarized).bucket)
    }

    @Test
    fun `skips buckets that do not reach far enough back`() {
        val plan = planner.plan(
            rangeFrom(day = 2),
            retention(events = day(10), daily = day(8), monthly = day(1)),
        )

        assertEquals(AuditBucket.MONTHLY, (plan as AuditPlan.Summarized).bucket)
    }

    @Test
    fun `reports unavailable when no source reaches back`() {
        val plan = planner.plan(rangeFrom(day = 1), retention(events = day(10), daily = day(8)))

        assertEquals(day(8), (plan as AuditPlan.Unavailable).earliestAvailableMillis)
    }

    @Test
    fun `reports unavailable when the device returned nothing`() {
        val plan = planner.plan(rangeFrom(day = 1), RetentionWindows(null, emptyMap()))

        assertEquals(null, (plan as AuditPlan.Unavailable).earliestAvailableMillis)
    }

    @Test
    fun `a long range starting inside the event window stays detailed`() {
        // Manda dónde empieza el rango, no cuánto dura
        val plan = planner.plan(
            AuditRange(day(10), day(28)),
            retention(events = day(10), monthly = day(1)),
        )

        assertTrue(plan is AuditPlan.Detailed)
    }

    @Test
    fun `earliest available looks across every source`() {
        val earliest = planner.earliestAvailable(
            retention(events = day(10), daily = day(8), monthly = day(2), yearly = day(1)),
        )

        assertEquals(day(1), earliest)
    }

    private fun rangeFrom(day: Int) = AuditRange(day(day), day(28))

    private fun retention(
        events: Long? = null,
        daily: Long? = null,
        weekly: Long? = null,
        monthly: Long? = null,
        yearly: Long? = null,
    ) = RetentionWindows(
        earliestEventMillis = events,
        earliestByBucket = mapOf(
            AuditBucket.DAILY to daily,
            AuditBucket.WEEKLY to weekly,
            AuditBucket.MONTHLY to monthly,
            AuditBucket.YEARLY to yearly,
        ),
    )

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Lima")

        fun day(day: Int): Long =
            ZonedDateTime.of(2026, 10, day, 0, 0, 0, 0, ZONE).toInstant().toEpochMilli()
    }
}
