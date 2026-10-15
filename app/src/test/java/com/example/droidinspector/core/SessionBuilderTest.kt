package com.example.droidinspector.core

import com.example.droidinspector.shared.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos que el sistema entrega mal formados: sin salida, solapados o desordenados. */
class SessionBuilderTest {

    private val builder = SessionBuilder()
    private val range = AuditRange(at(10, 0), at(12, 0))

    @Test
    fun `pairs foreground with background`() {
        val sessions = builder.build(
            listOf(foreground("chat", at(10, 9)), background("chat", at(10, 10))),
            range,
        )

        assertEquals(1, sessions.size)
        assertEquals(60 * MINUTE, sessions.single().durationMillis)
    }

    @Test
    fun `closes a session left open by a session break`() {
        val sessions = builder.build(
            listOf(foreground("chat", at(10, 9)), sessionBreak(at(10, 11))),
            range,
        )

        assertEquals(2 * 60 * MINUTE, sessions.single().durationMillis)
    }

    @Test
    fun `closes a session with no closing event at the end of the range`() {
        val sessions = builder.build(listOf(foreground("chat", at(11, 23))), range)

        assertEquals(range.endMillis, sessions.single().endMillis)
        assertEquals(60 * MINUTE, sessions.single().durationMillis)
    }

    @Test
    fun `a foreground of another app closes the one left open`() {
        // Caso real: la app anterior se queda sin evento de salida y su sesión se infla
        val sessions = builder.build(
            listOf(
                foreground("chat", at(10, 9)),
                foreground("maps", at(10, 9, 30)),
                background("maps", at(10, 10)),
            ),
            range,
        )

        assertEquals(2, sessions.size)
        val chat = sessions.single { it.packageName == "chat" }
        assertEquals(30 * MINUTE, chat.durationMillis)
        assertEquals(at(10, 9, 30), chat.endMillis)
    }

    @Test
    fun `sessions never overlap, so a day cannot exceed real time`() {
        val sessions = builder.build(
            listOf(
                foreground("chat", at(10, 9)),
                foreground("maps", at(10, 10)),
                foreground("mail", at(10, 11)),
                background("mail", at(10, 12)),
            ),
            range,
        )

        assertEquals(3 * 60 * MINUTE, sessions.sumOf { it.durationMillis })
        sessions.zipWithNext { first, second ->
            assertTrue("sesiones solapadas", first.endMillis <= second.startMillis)
        }
    }

    @Test
    fun `ignores a background with no previous foreground`() {
        assertTrue(builder.build(listOf(background("chat", at(10, 10))), range).isEmpty())
    }

    @Test
    fun `a repeated foreground does not open a second session`() {
        val sessions = builder.build(
            listOf(
                foreground("chat", at(10, 9)),
                foreground("chat", at(10, 9, 30)),
                background("chat", at(10, 10)),
            ),
            range,
        )

        assertEquals(1, sessions.size)
        assertEquals(60 * MINUTE, sessions.single().durationMillis)
    }

    @Test
    fun `clamps a session that starts before the range`() {
        val sessions = builder.build(
            listOf(foreground("chat", at(9, 23)), background("chat", at(10, 1))),
            range,
        )

        assertEquals(range.startMillis, sessions.single().startMillis)
        assertEquals(60 * MINUTE, sessions.single().durationMillis)
    }

    @Test
    fun `drops a session outside the range`() {
        val sessions = builder.build(
            listOf(foreground("chat", at(9, 1)), background("chat", at(9, 2))),
            range,
        )

        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `drops a zero length session`() {
        val sessions = builder.build(
            listOf(foreground("chat", at(10, 10)), background("chat", at(10, 10))),
            range,
        )

        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `handles events out of order`() {
        val sessions = builder.build(
            listOf(background("chat", at(10, 10)), foreground("chat", at(10, 9))),
            range,
        )

        assertEquals(60 * MINUTE, sessions.single().durationMillis)
    }

    @Test
    fun `caps a session that another app closed hours later`() {
        // El equipo se apagó sin avisar: el hueco no es uso real
        val sessions = builder.build(
            listOf(foreground("chat", at(10, 9)), foreground("maps", at(11, 9))),
            range,
        )

        val chat = sessions.single { it.packageName == "chat" }
        assertEquals(CAP, chat.durationMillis)
    }

    @Test
    fun `caps a session closed only by the screen going off`() {
        // Apagar la pantalla no dice cuándo se dejó de usar la app
        val sessions = builder.build(
            listOf(foreground("chat", at(10, 9)), sessionBreak(at(10, 23))),
            range,
        )

        assertEquals(CAP, sessions.single().durationMillis)
    }

    @Test
    fun `does not cap a session that reported its own exit`() {
        val sessions = builder.build(
            listOf(foreground("chat", at(10, 9)), background("chat", at(10, 23))),
            range,
        )

        assertEquals(14 * 60 * MINUTE, sessions.single().durationMillis)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val CAP = Config.MAX_UNCLOSED_SESSION_MILLIS
        val ZONE: ZoneId = ZoneId.of("America/Lima")

        fun at(day: Int, hour: Int, minute: Int = 0): Long =
            ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli()

        fun foreground(packageName: String, millis: Long) =
            UsageEvent(packageName, millis, UsageEvent.Type.FOREGROUND)

        fun background(packageName: String, millis: Long) =
            UsageEvent(packageName, millis, UsageEvent.Type.BACKGROUND)

        fun sessionBreak(millis: Long) =
            UsageEvent("", millis, UsageEvent.Type.SESSION_BREAK)
    }
}
