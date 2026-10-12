package com.example.droidinspector.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

/** Nombre visible e icono de una app; `packageName` si el sistema ya no la conoce. */
data class AppLabel(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * Resuelve nombre e icono por paquete. Sin caché propia: `PackageManager` ya cachea y
 * una auditoría consulta decenas de paquetes, no miles.
 */
class AppCatalog(private val packageManager: PackageManager) {

    fun labels(packageNames: Collection<String>): Map<String, AppLabel> =
        packageNames.distinct().associateWith { label(it) }

    private fun label(packageName: String): AppLabel {
        val info = applicationInfoOrNull(packageName)
            ?: return AppLabel(packageName, packageName, null)
        return AppLabel(
            packageName = packageName,
            label = packageManager.getApplicationLabel(info).toString(),
            icon = runCatching { packageManager.getApplicationIcon(info) }.getOrNull(),
        )
    }

    private fun applicationInfoOrNull(packageName: String): ApplicationInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
    } catch (notInstalled: PackageManager.NameNotFoundException) {
        null
    }
}
