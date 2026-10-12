package com.example.droidinspector.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.droidinspector.core.AuditRange
import com.example.droidinspector.core.UsageEvent

/**
 * Lee los eventos de uso del sistema y los traduce al modelo de `core`, que no
 * conoce `android.*`. El acceso a datos de uso es un permiso especial: no se pide
 * con un diálogo, el usuario lo concede en Ajustes.
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
        val cursor = manager.queryEvents(range.startMillis, range.endMillis)
        val systemEvent = UsageEvents.Event()
        while (cursor.hasNextEvent()) {
            cursor.getNextEvent(systemEvent)
            val type = typeOf(systemEvent.eventType) ?: continue
            events += UsageEvent(
                packageName = systemEvent.packageName.orEmpty(),
                timestampMillis = systemEvent.timeStamp,
                type = type,
            )
        }
        return events
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
}
