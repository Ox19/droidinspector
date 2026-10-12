package com.example.droidinspector.data

import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.droidinspector.MainActivity
import com.example.droidinspector.core.AuditRange
import com.example.droidinspector.core.UsageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Lo que solo se puede comprobar contra el sistema: permiso especial y eventos reales. */
@RunWith(AndroidJUnit4::class)
class UsageStatsSourceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = UsageStatsSource(context)

    @Before
    fun grantUsageAccess() {
        // Es un acceso especial: no hay diálogo, se concede por appops
        shell("appops set ${context.packageName} GET_USAGE_STATS allow")
    }

    @Test
    fun reports_the_permission_as_granted() {
        assertTrue(source.hasPermission())
    }

    @Test
    fun reports_the_permission_as_denied_when_revoked() {
        shell("appops set ${context.packageName} GET_USAGE_STATS deny")
        try {
            assertTrue(!source.hasPermission())
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS allow")
        }
    }

    @Test
    fun returns_events_inside_the_requested_range() {
        val range = lastHours(24)

        val events = source.events(range)

        events.forEach { event ->
            assertTrue(
                "evento fuera del rango: ${event.timestampMillis}",
                event.timestampMillis in range.startMillis..range.endMillis,
            )
            assertTrue("paquete vacío", event.packageName.isNotEmpty() ||
                event.type == UsageEvent.Type.SESSION_BREAK)
        }
    }

    @Test
    fun sees_this_app_going_to_foreground() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val ownForeground = awaitEvent { event ->
                event.packageName == context.packageName &&
                    event.type == UsageEvent.Type.FOREGROUND
            }
            assertTrue("el sistema no reportó el paso a primer plano", ownForeground)
        }
    }

    @Test
    fun returns_nothing_for_an_empty_range() {
        val now = System.currentTimeMillis()

        assertEquals(emptyList<UsageEvent>(), source.events(AuditRange(now, now)))
    }

    /** El sistema tarda en publicar los eventos, así que se reintenta antes de fallar. */
    private fun awaitEvent(matcher: (UsageEvent) -> Boolean): Boolean {
        repeat(ATTEMPTS) {
            if (source.events(lastHours(1)).any(matcher)) return true
            Thread.sleep(RETRY_DELAY_MILLIS)
        }
        return false
    }

    private fun lastHours(hours: Int): AuditRange {
        val now = System.currentTimeMillis()
        return AuditRange(now - hours * 60L * 60L * 1000L, now)
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val ATTEMPTS = 10
        const val RETRY_DELAY_MILLIS = 500L
    }
}
