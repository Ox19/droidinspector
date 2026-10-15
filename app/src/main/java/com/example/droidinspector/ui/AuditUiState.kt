package com.example.droidinspector.ui

import com.example.droidinspector.core.AuditReport
import com.example.droidinspector.data.AppLabel

/** Pasos de la auditoría, en el orden en que los recorre el usuario. */
sealed interface AuditUiState {
    data object Consent : AuditUiState
    data object PermissionRequired : AuditUiState
    data object RangeSelection : AuditUiState
    data object Loading : AuditUiState

    data class Ready(
        val report: AuditReport,
        val labels: Map<String, AppLabel>,
        val export: ExportState = ExportState.Idle,
    ) : AuditUiState

    data class Failed(val message: String) : AuditUiState
}

/** La exportación ocurre sobre el dashboard ya visible, así que es un estado aparte. */
sealed interface ExportState {
    data object Idle : ExportState
    data object Running : ExportState
    data class Done(val displayName: String) : ExportState
    data class Failed(val message: String) : ExportState
}
