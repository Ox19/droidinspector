package com.example.droidinspector.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.example.droidinspector.R
import com.example.droidinspector.core.AppUsage
import com.example.droidinspector.core.AuditDetail
import com.example.droidinspector.core.AuditRange
import com.example.droidinspector.core.AuditReport
import com.example.droidinspector.data.AppLabel
import com.example.droidinspector.shared.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun AuditScreen(viewModel: AuditViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // El permiso se concede fuera de la app: hay que revalidarlo al volver
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            when (val current = state) {
                AuditUiState.Consent -> MessageStep(
                    title = stringResource(R.string.consent_title),
                    body = stringResource(R.string.consent_body),
                    actionLabel = stringResource(R.string.consent_accept),
                    onAction = viewModel::acceptConsent,
                )

                AuditUiState.PermissionRequired -> MessageStep(
                    title = stringResource(R.string.permission_title),
                    body = stringResource(R.string.permission_body),
                    actionLabel = stringResource(R.string.permission_open_settings),
                    onAction = { context.startActivity(viewModel.settingsIntent()) },
                )

                AuditUiState.RangeSelection -> RangeStep(onRun = viewModel::runAudit)

                AuditUiState.Loading -> CenteredProgress()

                is AuditUiState.Ready -> Dashboard(
                    report = current.report,
                    labels = current.labels,
                    export = current.export,
                    onExport = viewModel::exportReport,
                    onNewAudit = viewModel::selectRange,
                )

                is AuditUiState.Failed -> MessageStep(
                    title = stringResource(R.string.error_title),
                    body = current.message,
                    actionLabel = stringResource(R.string.error_retry),
                    onAction = viewModel::selectRange,
                )
            }
        }
    }
}

@Composable
private fun MessageStep(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeStep(onRun: (AuditRange) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val pickerState = rememberDateRangePickerState(
        selectableDates = remember(zone) { retentionDates(zone) },
    )
    val start = pickerState.selectedStartDateMillis
    val end = pickerState.selectedEndDateMillis

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.range_retention_notice, Config.EVENT_RETENTION_DAYS.toInt()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        Button(
            onClick = { onRun(auditRangeOf(start!!, end ?: start, zone)) },
            enabled = start != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Text(stringResource(R.string.range_run))
        }
    }
}

@Composable
private fun CenteredProgress() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Dashboard(
    report: AuditReport,
    labels: Map<String, AppLabel>,
    export: ExportState,
    onExport: () -> Unit,
    onNewAudit: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "${formatDay(report.range.startMillis, zone)} – " +
                    formatDay(report.range.endMillis - 1, zone),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
            SummaryRow(report)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.dashboard_apps_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (report.appUsages.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.dashboard_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        items(report.appUsages, key = { it.packageName }) { usage ->
            AppUsageRow(usage, labels[usage.packageName], report.detail)
            HorizontalDivider()
        }

        item {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onExport,
                enabled = export !is ExportState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (export is ExportState.Running) {
                        stringResource(R.string.dashboard_exporting)
                    } else {
                        stringResource(R.string.dashboard_export)
                    },
                )
            }
            ExportMessage(export)
            TextButton(
                onClick = onNewAudit,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Text(stringResource(R.string.dashboard_new_audit))
            }
        }
    }
}

@Composable
private fun ExportMessage(export: ExportState) {
    val message = when (export) {
        is ExportState.Done -> stringResource(R.string.dashboard_exported, export.displayName)
        is ExportState.Failed -> stringResource(R.string.dashboard_export_failed, export.message)
        else -> null
    } ?: return

    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = if (export is ExportState.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SummaryRow(report: AuditReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val detail = report.detail
        SummaryCell(
            // El total no mide lo mismo en cada modo, así que tampoco se llama igual
            stringResource(
                if (detail is AuditDetail.Detailed) {
                    R.string.summary_total_time
                } else {
                    R.string.summary_foreground_time
                },
            ),
            formatDuration(report.totalUsageMillis),
        )
        SummaryCell(stringResource(R.string.summary_apps), report.appUsages.size.toString())
        if (detail is AuditDetail.Detailed) {
            SummaryCell(
                stringResource(R.string.summary_launches),
                detail.perApp.values.sumOf { it.launchCount }.toString(),
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AppUsageRow(usage: AppUsage, label: AppLabel?, detail: AuditDetail) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(label?.icon)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label?.label ?: usage.packageName, style = MaterialTheme.typography.bodyLarge)
            val launches = (detail as? AuditDetail.Detailed)?.perApp?.get(usage.packageName)
            Text(
                if (launches != null) {
                    pluralStringResource(
                        R.plurals.dashboard_launches,
                        launches.launchCount,
                        launches.launchCount,
                    )
                } else {
                    stringResource(
                        R.string.dashboard_last_use,
                        formatDay(usage.lastUseMillis, ZoneId.systemDefault()),
                    )
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(formatDuration(usage.totalMillis), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppIcon(drawable: Drawable?) {
    if (drawable == null) {
        Spacer(Modifier.size(40.dp))
        return
    }
    // Tamaño explícito: algunos drawables no declaran dimensiones intrínsecas
    val bitmap = remember(drawable) { drawable.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
}

/** El picker entrega medianoche UTC; el rango se arma en la zona del dispositivo. */
private fun auditRangeOf(startUtcMillis: Long, endUtcMillis: Long, zone: ZoneId): AuditRange {
    val startDate = dateAtUtc(startUtcMillis)
    val endDate = dateAtUtc(endUtcMillis)
    val endExclusive = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return AuditRange(
        startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillis = minOf(endExclusive, System.currentTimeMillis()),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
private fun retentionDates(zone: ZoneId): SelectableDates {
    val today = LocalDate.now(zone)
    val earliest = today.minusDays(Config.EVENT_RETENTION_DAYS)
    return object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val date = dateAtUtc(utcTimeMillis)
            return !date.isBefore(earliest) && !date.isAfter(today)
        }

        override fun isSelectableYear(year: Int): Boolean =
            year == today.year || year == earliest.year
    }
}

private fun dateAtUtc(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private const val ICON_SIZE_PX = 96
