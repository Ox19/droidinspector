package com.example.droidinspector.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.droidinspector.core.AuditAggregator
import com.example.droidinspector.core.AuditPlan
import com.example.droidinspector.core.AuditPlanner
import com.example.droidinspector.core.AuditRange
import com.example.droidinspector.core.AuditReport
import com.example.droidinspector.core.BucketAggregator
import com.example.droidinspector.core.InstallEventsBuilder
import com.example.droidinspector.core.RetentionWindows
import com.example.droidinspector.core.SessionBuilder
import com.example.droidinspector.data.AppCatalog
import com.example.droidinspector.data.PdfReportWriter
import com.example.droidinspector.data.UsageStatsSource
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orquestador: carga con `data`, calcula con `core` y publica el estado de la pantalla.
 * Es el único punto que conoce ambas capas.
 */
class AuditViewModel(
    private val usageStats: UsageStatsSource,
    private val catalog: AppCatalog,
    private val sessionBuilder: SessionBuilder,
    private val aggregator: AuditAggregator,
    private val bucketAggregator: BucketAggregator,
    private val planner: AuditPlanner,
    private val reportWriter: PdfReportWriter,
) : ViewModel() {

    private val mutableState = MutableStateFlow<AuditUiState>(AuditUiState.Consent)
    val state: StateFlow<AuditUiState> = mutableState.asStateFlow()

    fun acceptConsent() = evaluatePermission()

    /** Reporte de muestra para el PoC: no mide el dispositivo. Ver README > Modo demostración. */
    fun showDemo() {
        val report = DemoData.report(ZoneId.systemDefault())
        mutableState.value = AuditUiState.Ready(
            report = report,
            labels = catalog.labels(DemoData.packages()),
        )
    }

    /**
     * Solo al volver de Ajustes, que es donde se concede. No actúa sobre el consentimiento:
     * si lo hiciera, la pantalla se saltaría cuando el permiso ya está dado.
     */
    fun refreshPermission() {
        if (mutableState.value is AuditUiState.PermissionRequired) evaluatePermission()
    }

    private fun evaluatePermission() {
        if (usageStats.hasPermission()) {
            selectRange()
        } else {
            mutableState.value = AuditUiState.PermissionRequired
        }
    }

    /** Qué conserva este dispositivo: define hasta dónde se puede auditar y con qué detalle. */
    private fun measureRetention() {
        viewModelScope.launch {
            val windows = withContext(Dispatchers.IO) { usageStats.retentionProbe().windows }
            val current = mutableState.value
            if (current is AuditUiState.RangeSelection) {
                mutableState.value = current.copy(retention = windows)
            }
        }
    }

    /** Qué modo saldría para un rango, para avisar antes de generar. */
    fun planFor(range: AuditRange): AuditPlan? {
        val retention = (mutableState.value as? AuditUiState.RangeSelection)?.retention
        return retention?.let { planner.plan(range, it) }
    }

    fun settingsIntent(): Intent = usageStats.settingsIntent()

    fun selectRange() {
        mutableState.value = AuditUiState.RangeSelection()
        measureRetention()
    }

    fun runAudit(range: AuditRange) {
        val retention = (mutableState.value as? AuditUiState.RangeSelection)?.retention
            ?: RetentionWindows(null, emptyMap())
        mutableState.value = AuditUiState.Loading
        viewModelScope.launch {
            mutableState.value = try {
                withContext(Dispatchers.IO) { audit(range, retention) }
            } catch (failure: RuntimeException) {
                AuditUiState.Failed(failure.message.orEmpty())
            }
        }
    }

    /** Exporta el dashboard ya calculado; no vuelve a leer el sistema. */
    fun exportReport() {
        val ready = mutableState.value as? AuditUiState.Ready ?: return
        if (ready.export is ExportState.Running) return

        mutableState.value = ready.copy(export = ExportState.Running)
        viewModelScope.launch {
            val result = try {
                val written = withContext(Dispatchers.IO) {
                    reportWriter.write(ready.report, ready.labels)
                }
                ExportState.Done(written.displayName)
            } catch (failure: Exception) {
                ExportState.Failed(failure.message.orEmpty())
            }
            mutableState.value = ready.copy(export = result)
        }
    }

    private fun audit(range: AuditRange, retention: RetentionWindows): AuditUiState {
        // Sin permiso el sistema devuelve vacío: sin esto, el reporte diría "no usó nada"
        if (!usageStats.hasPermission()) return AuditUiState.PermissionRequired

        val base = when (val plan = planner.plan(range, retention)) {
            is AuditPlan.Detailed -> detailedReport(plan.range)
            is AuditPlan.Summarized -> bucketAggregator.aggregate(
                usageStats.buckets(plan.range, plan.bucket),
                plan.range,
                plan.bucket,
            )
            is AuditPlan.Unavailable -> return AuditUiState.Failed(UNAVAILABLE)
        }

        // Instalaciones y actualizaciones del periodo: dato aparte del uso, de PackageManager
        val report = base.copy(
            installEvents = installEventsBuilder.build(catalog.installedPackages(), range),
        )
        val packages = report.appUsages.map { it.packageName } +
            report.installEvents.map { it.packageName }

        return AuditUiState.Ready(
            report = report,
            labels = catalog.labels(packages),
        )
    }

    private val installEventsBuilder = InstallEventsBuilder()

    private fun detailedReport(range: AuditRange): AuditReport =
        aggregator.aggregate(sessionBuilder.build(usageStats.events(range), range), range)

    companion object {
        /** Marca para que la pantalla resuelva el texto; el ViewModel no conoce recursos. */
        const val UNAVAILABLE = "unavailable"

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AuditViewModel(
                    usageStats = UsageStatsSource(appContext),
                    catalog = AppCatalog(appContext.packageManager),
                    sessionBuilder = SessionBuilder(),
                    aggregator = AuditAggregator(ZoneId.systemDefault()),
                    bucketAggregator = BucketAggregator(ZoneId.systemDefault()),
                    planner = AuditPlanner(),
                    reportWriter = PdfReportWriter(appContext),
                )
            }
        }
    }
}
