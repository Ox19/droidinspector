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
    ) : AuditUiState

    data class Failed(val message: String) : AuditUiState
}
