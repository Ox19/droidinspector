package com.example.droidinspector.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.droidinspector.core.AuditAggregator
import com.example.droidinspector.core.AuditRange
import com.example.droidinspector.core.SessionBuilder
import com.example.droidinspector.data.AppCatalog
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
) : ViewModel() {

    private val mutableState = MutableStateFlow<AuditUiState>(AuditUiState.Consent)
    val state: StateFlow<AuditUiState> = mutableState.asStateFlow()

    fun acceptConsent() = evaluatePermission()

    /**
     * Solo al volver de Ajustes, que es donde se concede. No actúa sobre el consentimiento:
     * si lo hiciera, la pantalla se saltaría cuando el permiso ya está dado.
     */
    fun refreshPermission() {
        if (mutableState.value is AuditUiState.PermissionRequired) evaluatePermission()
    }

    private fun evaluatePermission() {
        mutableState.value = if (usageStats.hasPermission()) {
            AuditUiState.RangeSelection
        } else {
            AuditUiState.PermissionRequired
        }
    }

    fun settingsIntent(): Intent = usageStats.settingsIntent()

    fun selectRange() {
        mutableState.value = AuditUiState.RangeSelection
    }

    fun runAudit(range: AuditRange) {
        mutableState.value = AuditUiState.Loading
        viewModelScope.launch {
            mutableState.value = try {
                withContext(Dispatchers.IO) { audit(range) }
            } catch (failure: RuntimeException) {
                AuditUiState.Failed(failure.message.orEmpty())
            }
        }
    }

    private fun audit(range: AuditRange): AuditUiState.Ready {
        val sessions = sessionBuilder.build(usageStats.events(range), range)
        val report = aggregator.aggregate(sessions, range)
        return AuditUiState.Ready(
            report = report,
            labels = catalog.labels(report.appUsages.map { it.packageName }),
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AuditViewModel(
                    usageStats = UsageStatsSource(appContext),
                    catalog = AppCatalog(appContext.packageManager),
                    sessionBuilder = SessionBuilder(),
                    aggregator = AuditAggregator(ZoneId.systemDefault()),
                )
            }
        }
    }
}
