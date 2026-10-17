package com.example.droidinspector.core

/**
 * Convierte las fechas de instalación/actualización que da el sistema en eventos dentro del
 * rango auditado. No hay desinstalaciones: Android no conserva ese dato para un app normal.
 */
class InstallEventsBuilder {

    fun build(packages: List<InstalledPackage>, range: AuditRange): List<PackageEvent> {
        val events = mutableListOf<PackageEvent>()
        for (app in packages) {
            if (app.firstInstallMillis in range) {
                events += PackageEvent(app.packageName, app.firstInstallMillis, PackageEvent.Type.INSTALL)
            }
            // Una actualización solo cuenta si no coincide con la instalación (si no, es el mismo hecho)
            if (app.lastUpdateMillis != app.firstInstallMillis && app.lastUpdateMillis in range) {
                events += PackageEvent(app.packageName, app.lastUpdateMillis, PackageEvent.Type.UPDATE)
            }
        }
        return events.sortedBy { it.timestampMillis }
    }

    private operator fun AuditRange.contains(millis: Long): Boolean =
        millis in startMillis until endMillis
}
