package com.example.droidinspector.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.content.pm.PackageInfo
import android.os.Build
import com.example.droidinspector.core.InstalledPackage

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

    /** ¿La app está instalada y es visible al usuario? */
    fun isInstalled(packageName: String): Boolean =
        packageManager.getLaunchIntentForPackage(packageName) != null

    /**
     * Apps visibles al usuario (con icono en el menú) con sus fechas de instalación y
     * última actualización. Las de sistema sin icono se omiten: no son algo que el usuario
     * "instaló", y llenarían el reporte de ruido.
     */
    fun installedPackages(): List<InstalledPackage> =
        installedPackageInfos()
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map { InstalledPackage(it.packageName, it.firstInstallTime, it.lastUpdateTime) }

    private fun installedPackageInfos(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }

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
