package com.example.droidinspector.core

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallEventsBuilderTest {

    private val builder = InstallEventsBuilder()
    private val range = AuditRange(at(10, 1), at(10, 31))

    @Test
    fun `an install inside the range is reported`() {
        val events = builder.build(
            listOf(InstalledPackage("chat", at(10, 5), at(10, 5))),
            range,
        )

        assertEquals(1, events.size)
        assertEquals(PackageEvent.Type.INSTALL, events.single().type)
    }

    @Test
    fun `an app installed before but updated inside reports only the update`() {
        val events = builder.build(
            listOf(InstalledPackage("chat", at(9, 1), at(10, 10))),
            range,
        )

        assertEquals(listOf(PackageEvent.Type.UPDATE), events.map { it.type })
    }

    @Test
    fun `install and update in range both appear, in time order`() {
        val events = builder.build(
            listOf(InstalledPackage("chat", at(10, 3), at(10, 20))),
            range,
        )

        assertEquals(listOf(PackageEvent.Type.INSTALL, PackageEvent.Type.UPDATE), events.map { it.type })
        assertTrue(events[0].timestampMillis < events[1].timestampMillis)
    }

    @Test
    fun `nothing outside the range`() {
        val events = builder.build(
            listOf(InstalledPackage("chat", at(9, 1), at(9, 2))),
            range,
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `install equal to update counts once`() {
        val events = builder.build(
            listOf(InstalledPackage("chat", at(10, 5), at(10, 5))),
            range,
        )

        assertEquals(1, events.size)
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Lima")
        fun at(month: Int, day: Int): Long =
            ZonedDateTime.of(2026, month, day, 12, 0, 0, 0, ZONE).toInstant().toEpochMilli()
    }
}
