package com.example.droidinspector.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.droidinspector.core.AuditBucket
import com.example.droidinspector.core.AuditRange
import com.example.droidinspector.core.RetentionWindows
import com.example.droidinspector.core.UsageBucket
import com.example.droidinspector.core.UsageEvent
import com.example.droidinspector.shared.Config

/** Hasta dónde llega el historial de este dispositivo, y cuántos agregados devolvió. */
data class RetentionProbe(
    val windows: RetentionWindows,
    val bucketCounts: Map<AuditBucket, Int>,
)

/**
 * Lee del sistema las dos fuentes de uso y las traduce al modelo de `core`, que no conoce
 * `android.*`: los eventos finos (detalle, pocos días) y los agregados por periodo
 * (sin detalle, meses o años).
 *
 * El acceso a datos de uso es un permiso especial: no se pide con un diálogo, el usuario
 * lo concede en Ajustes. Sin él, el sistema **no se comporta igual en todas partes**: un
 * Samsung con Android 13 lanza `SecurityException` donde el emulador devuelve vacío. Aquí
 * se normaliza a vacío para no tumbar la app, así que quien llame debe comprobar
 * `hasPermission()` antes o confundirá "sin permiso" con "no usó nada".
 */
class UsageStatsSource(private val context: Context) {

    fun hasPermission(): Boolean {
        val appOps = ContextCompat.getSystemService(context, AppOpsManager::class.java)
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        // MODE_DEFAULT delega la decisión al permiso declarado en el manifest
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkPermission(
                android.Manifest.permission.PACKAGE_USAGE_STATS,
                Process.myPid(),
                Process.myUid(),
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Pantalla de Ajustes donde se concede el acceso; la UI lleva al usuario hasta aquí. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * Eventos del rango, en el orden que los entregue el sistema. Los que no describen
     * entrada o salida de primer plano se descartan.
     */
    fun events(range: AuditRange): List<UsageEvent> {
        val manager = ContextCompat.getSystemService(context, UsageStatsManager::class.java)
            ?: return emptyList()

        val events = mutableListOf<UsageEvent>()
        val cursor = try {
            manager.queryEvents(range.startMillis, range.endMillis)
        } catch (denied: SecurityException) {
            return emptyList()
        }
        val systemEvent = UsageEvents.Event()
        while (cursor.hasNextEvent()) {
            cursor.getNextEvent(systemEvent)
            val type = typeOf(systemEvent.eventType) ?: continue
            val packageName = systemEvent.packageName.orEmpty()
            if (type != UsageEvent.Type.SESSION_BREAK && packageName in excludedPackages()) continue
            events += UsageEvent(packageName, systemEvent.timeStamp, type)
        }
        return events
    }

    /**
     * Agregados del periodo. Se traducen uno a uno: fusionarlos o descartarlos es regla de
     * negocio y vive en `core`. El sistema los devuelve repetidos y a veces con cifras
     * imposibles, y esa decisión no se toma aquí.
     */
    fun buckets(range: AuditRange, bucket: AuditBucket): List<UsageBucket> {
        val manager = ContextCompat.getSystemService(context, UsageStatsManager::class.java)
            ?: return emptyList()

        val stats = try {
            manager.queryUsageStats(intervalOf(bucket), range.startMillis, range.endMillis)
        } catch (denied: SecurityException) {
            // Comportamiento observado en Samsung/Android 13; el emulador devuelve vacío
            return emptyList()
        }

        return stats.orEmpty()
            .filter { it.packageName !in excludedPackages() }
            .map { bucketStats ->
                UsageBucket(
                    packageName = bucketStats.packageName.orEmpty(),
                    startMillis = bucketStats.firstTimeStamp,
                    endMillis = bucketStats.lastTimeStamp,
                    foregroundMillis = bucketStats.totalTimeInForeground,
                    lastUsedMillis = bucketStats.lastTimeUsed,
                )
            }
    }

    /**
     * Mide hasta dónde llega hacia atrás cada fuente. Los fabricantes podan distinto, así
     * que el alcance real se consulta en vez de asumirse.
     */
    fun retentionProbe(): RetentionProbe {
        val now = System.currentTimeMillis()
        val lookback = AuditRange(now - Config.RETENTION_PROBE_LOOKBACK_DAYS * DAY_MILLIS, now)
        val perBucket = AuditBucket.entries.associateWith { bucket ->
            // Un bucket sin uso no prueba que haya historia: solo cuentan los que tienen datos
            buckets(lookback, bucket).filter { it.foregroundMillis > 0 }
        }

        return RetentionProbe(
            windows = RetentionWindows(
                earliestEventMillis = events(
                    AuditRange(now - Config.EVENT_PROBE_LOOKBACK_DAYS * DAY_MILLIS, now),
                ).minOfOrNull { it.timestampMillis },
                earliestByBucket = perBucket.mapValues { (_, found) ->
                    found.minOfOrNull { it.startMillis }
                },
            ),
            bucketCounts = perBucket.mapValues { (_, found) -> found.size },
        )
    }

    /**
     * Superficies que no son apps que el usuario "usa": la pantalla de inicio del equipo
     * (se detecta la real, no una lista fija) y la interfaz del sistema. Excluirlas alinea
     * la auditoría con lo que mide Bienestar Digital.
     */
    private fun excludedPackages(): Set<String> {
        if (cachedExcluded != null) return cachedExcluded!!
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcher: ResolveInfo? = context.packageManager.resolveActivity(home, 0)
        cachedExcluded = setOfNotNull(
            launcher?.activityInfo?.packageName,
            "com.android.systemui",
        )
        return cachedExcluded!!
    }

    private var cachedExcluded: Set<String>? = null

    private fun intervalOf(bucket: AuditBucket): Int = when (bucket) {
        AuditBucket.DAILY -> UsageStatsManager.INTERVAL_DAILY
        AuditBucket.WEEKLY -> UsageStatsManager.INTERVAL_WEEKLY
        AuditBucket.MONTHLY -> UsageStatsManager.INTERVAL_MONTHLY
        AuditBucket.YEARLY -> UsageStatsManager.INTERVAL_YEARLY
    }

    private fun typeOf(systemEventType: Int): UsageEvent.Type? = when (systemEventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Type.FOREGROUND
        // STOPPED llega después de PAUSED; el segundo queda huérfano y se ignora al construir sesiones
        UsageEvents.Event.ACTIVITY_PAUSED,
        UsageEvents.Event.ACTIVITY_STOPPED -> UsageEvent.Type.BACKGROUND
        // Pantalla apagada o equipo apagado cierran todo lo que quedó abierto
        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
        UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEvent.Type.SESSION_BREAK
        else -> null
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
