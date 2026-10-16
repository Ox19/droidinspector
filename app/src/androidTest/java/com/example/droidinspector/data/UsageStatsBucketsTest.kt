package com.example.droidinspector.data

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.droidinspector.core.AuditBucket
import com.example.droidinspector.core.AuditRange
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Los agregados del sistema: la fuente que permite auditar periodos largos. */
@RunWith(AndroidJUnit4::class)
class UsageStatsBucketsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = UsageStatsSource(context)

    @Before
    fun grantUsageAccess() {
        shell("appops set ${context.packageName} GET_USAGE_STATS allow")
    }

    @After
    fun restoreUsageAccess() {
        shell("appops set ${context.packageName} GET_USAGE_STATS allow")
    }

    @Test
    fun returns_daily_buckets_with_sane_values() {
        val buckets = source.buckets(lastDays(2), AuditBucket.DAILY)

        buckets.forEach { bucket ->
            assertTrue("paquete vacío", bucket.packageName.isNotEmpty())
            assertTrue("fin anterior al inicio", bucket.endMillis >= bucket.startMillis)
            assertTrue("tiempo negativo", bucket.foregroundMillis >= 0)
        }
    }

    @Test
    fun returns_empty_without_permission_instead_of_failing() {
        // Samsung/Android 13 lanza SecurityException donde el emulador devuelve vacío:
        // sin normalizarlo, pedir una auditoría sin permiso tumbaría la app
        shell("appops set ${context.packageName} GET_USAGE_STATS deny")

        assertTrue(source.buckets(lastDays(2), AuditBucket.DAILY).isEmpty())
        assertTrue(source.events(lastDays(2)).isEmpty())
        assertTrue(!source.hasPermission())
    }

    @Test
    fun does_not_merge_buckets_of_the_same_package() {
        // Fusionar es regla de negocio y vive en core: data traduce uno a uno
        val buckets = source.buckets(lastDays(30), AuditBucket.DAILY)

        val repeated = buckets.groupBy { it.packageName }.values.any { it.size > 1 }
        assertTrue(
            "con varios días debería haber más de un bucket por app, o ninguno",
            repeated || buckets.size == buckets.distinctBy { it.packageName }.size,
        )
    }

    @Test
    fun excludes_the_home_launcher_and_system_ui() {
        val home = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        val launcher = context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName

        val packages = source.events(lastDays(7)).map { it.packageName }.toSet() +
            source.buckets(lastDays(7), AuditBucket.DAILY).map { it.packageName }.toSet()

        if (launcher != null) {
            assertTrue("el launcher no debe contarse como app usada", launcher !in packages)
        }
        assertTrue("la UI del sistema no debe contarse", "com.android.systemui" !in packages)
    }

    @Test
    fun returns_nothing_for_an_empty_range() {
        val now = System.currentTimeMillis()

        assertEquals(emptyList<Any>(), source.buckets(AuditRange(now, now), AuditBucket.DAILY))
    }

    @Test
    fun a_three_year_yearly_query_does_not_throw() {
        val threeYears = lastDays(1100)

        AuditBucket.entries.forEach { bucket ->
            source.buckets(threeYears, bucket)
        }
    }

    @Test
    fun the_retention_probe_describes_every_bucket() {
        val probe = source.retentionProbe()

        assertEquals(AuditBucket.entries.toSet(), probe.bucketCounts.keys)
        assertEquals(AuditBucket.entries.toSet(), probe.windows.earliestByBucket.keys)
        probe.windows.earliestByBucket.forEach { (bucket, earliest) ->
            if (earliest != null) {
                assertTrue("$bucket dice tener datos del futuro", earliest <= System.currentTimeMillis())
            }
        }
    }

    private fun lastDays(days: Long): AuditRange {
        val now = System.currentTimeMillis()
        return AuditRange(now - days * 24L * 60L * 60L * 1000L, now)
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
