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
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.DisplayMode
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.droidinspector.core.AuditPlan
import com.example.droidinspector.core.PackageEvent
import com.example.droidinspector.core.AuditRange
import com.example.droidinspector.core.AuditReport
import com.example.droidinspector.core.RetentionWindows
import com.example.droidinspector.data.AppLabel
import com.example.droidinspector.shared.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
                .padding(padding),
        ) {
            when (val current = state) {
                AuditUiState.Consent -> MessageStep(
                    title = stringResource(R.string.consent_title),
                    body = stringResource(R.string.consent_body),
                    actionLabel = stringResource(R.string.consent_accept),
                    onAction = viewModel::acceptConsent,
                    secondaryLabel = stringResource(R.string.consent_demo),
                    onSecondary = viewModel::showDemo,
                )

                AuditUiState.PermissionRequired -> MessageStep(
                    title = stringResource(R.string.permission_title),
                    body = stringResource(R.string.permission_body),
                    actionLabel = stringResource(R.string.permission_open_settings),
                    onAction = { context.startActivity(viewModel.settingsIntent()) },
                )

                is AuditUiState.RangeSelection -> RangeStep(
                    retention = current.retention,
                    planFor = viewModel::planFor,
                    onRun = viewModel::runAudit,
                )

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
                    body = if (current.message == AuditViewModel.UNAVAILABLE) {
                        stringResource(R.string.mode_unavailable)
                    } else {
                        current.message
                    },
                    actionLabel = stringResource(R.string.error_retry),
                    onAction = viewModel::selectRange,
                )
            }
        }
    }
}

private val ScreenPadding = Modifier.padding(horizontal = 24.dp)

@Composable
private fun MessageStep(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(ScreenPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAction) { Text(actionLabel) }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeStep(
    retention: RetentionWindows?,
    planFor: (AuditRange) -> AuditPlan?,
    onRun: (AuditRange) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }

    // El selector se crea una sola vez con sus límites: si se construyera antes de medir,
    // conservaría el límite provisional y rechazaría fechas que sí tienen datos
    if (retention == null) {
        CenteredProgress(stringResource(R.string.range_measuring))
        return
    }

    // Con el zoom de pantalla alto quedan ~320 dp y el calendario de Material3 no se dibuja:
    // por debajo del mínimo se entra por teclado, que sí cabe
    val fitsCalendar = LocalConfiguration.current.screenWidthDp >= MIN_CALENDAR_WIDTH_DP
    val pickerState = rememberDateRangePickerState(
        selectableDates = remember(retention, zone) { availableDates(retention, zone) },
        initialDisplayMode = if (fitsCalendar) DisplayMode.Picker else DisplayMode.Input,
    )
    val start = pickerState.selectedStartDateMillis
    val end = pickerState.selectedEndDateMillis
    val range = start?.let { auditRangeOf(it, end ?: it, zone) }
    val tooLong = range != null && rangeDays(range) > Config.MAX_RANGE_DAYS

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.range_available_since, earliestLabel(retention, zone)),
            style = MaterialTheme.typography.bodySmall,
            modifier = ScreenPadding.padding(vertical = 8.dp),
        )
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.weight(1f),
            // En pantallas estrechas el encabezado parte el texto letra a letra
            title = null.takeIf { !fitsCalendar } ?: { DateRangePickerDefaults.DateRangePickerTitle(pickerState.displayMode) },
            headline = if (fitsCalendar) {
                {
                    DateRangePickerDefaults.DateRangePickerHeadline(
                        selectedStartDateMillis = pickerState.selectedStartDateMillis,
                        selectedEndDateMillis = pickerState.selectedEndDateMillis,
                        displayMode = pickerState.displayMode,
                        dateFormatter = remember { DatePickerDefaults.dateFormatter() },
                    )
                }
            } else {
                null
            },
        )
        Column(ScreenPadding) {
            if (tooLong) {
                Text(
                    stringResource(R.string.range_too_long, Config.MAX_RANGE_DAYS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                range?.let { ModeNotice(planFor(it)) }
            }
            Button(
                onClick = { onRun(range!!) },
                enabled = range != null && !tooLong,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text(stringResource(R.string.range_run))
            }
        }
    }
}

/** Avisa del detalle que se pierde **antes** de generar, no después. */
@Composable
private fun ModeNotice(plan: AuditPlan?) {
    val message = when (plan) {
        is AuditPlan.Detailed -> stringResource(R.string.mode_detailed)
        is AuditPlan.Summarized -> stringResource(R.string.mode_summarized)
        is AuditPlan.Unavailable -> stringResource(R.string.mode_unavailable)
        null -> return
    }
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = if (plan is AuditPlan.Unavailable) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun CenteredProgress(message: String = stringResource(R.string.loading)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
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
    LazyColumn(Modifier.fillMaxSize().then(ScreenPadding)) {
        item {
            // En modo resumido manda lo que cubren los buckets: decir solo lo pedido
            // dejaría un encabezado de un día junto a un total de cientos de horas
            val shown = (report.detail as? AuditDetail.Summarized)?.effectiveRange ?: report.range
            Text(
                "${formatDay(shown.startMillis, zone)} – " +
                    formatDay(shown.endMillis - 1, zone),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (report.detail is AuditDetail.Summarized) {
                Text(
                    stringResource(
                        R.string.dashboard_effective_range,
                        formatDay(report.range.startMillis, zone),
                        formatDay(report.range.endMillis - 1, zone),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

        if (report.installEvents.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.dashboard_activity_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(report.installEvents, key = { it.packageName + it.timestampMillis }) { event ->
                ActivityRow(event, labels[event.packageName])
                HorizontalDivider()
            }
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
private fun ActivityRow(event: PackageEvent, label: AppLabel?) {
    val zone = remember { ZoneId.systemDefault() }
    val activity = stringResource(
        when (event.type) {
            PackageEvent.Type.INSTALL -> R.string.pdf_install_installed
            PackageEvent.Type.UPDATE -> R.string.pdf_install_updated
            PackageEvent.Type.UNINSTALL -> R.string.pdf_install_uninstalled
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(label?.icon)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label?.label ?: event.packageName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${formatDay(event.timestampMillis, zone)} · " +
                    ACTIVITY_TIME.format(java.time.Instant.ofEpochMilli(event.timestampMillis).atZone(zone)),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(activity, style = MaterialTheme.typography.bodyMedium)
    }
}

private val ACTIVITY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

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
/** Días que abarca el rango, para no pasar del mes. */
private fun rangeDays(range: AuditRange): Long =
    (range.endMillis - range.startMillis) / (24L * 60L * 60L * 1000L)

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
private fun availableDates(retention: RetentionWindows?, zone: ZoneId): SelectableDates {
    val today = LocalDate.now(zone)
    val earliest = earliestDate(retention, zone) ?: today.minusDays(Config.EVENT_RETENTION_DAYS)
    return object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val date = dateAtUtc(utcTimeMillis)
            return !date.isBefore(earliest) && !date.isAfter(today)
        }

        override fun isSelectableYear(year: Int): Boolean = year in earliest.year..today.year
    }
}

/** La fecha más antigua con datos de cualquier fuente, acotada al tope de auditoría. */
private fun earliestDate(retention: RetentionWindows?, zone: ZoneId): LocalDate? {
    val earliestMillis = retention?.let { windows ->
        (windows.earliestByBucket.values + windows.earliestEventMillis).filterNotNull().minOrNull()
    } ?: return null
    val floor = LocalDate.now(zone).minusDays(Config.MAX_AUDIT_DAYS)
    return maxOf(Instant.ofEpochMilli(earliestMillis).atZone(zone).toLocalDate(), floor)
}

private fun earliestLabel(retention: RetentionWindows, zone: ZoneId): String =
    earliestDate(retention, zone)?.let { DATE_LABEL.format(it) } ?: "—"

private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun dateAtUtc(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private const val ICON_SIZE_PX = 96

/** Por debajo de este ancho, Material3 no dibuja el calendario del selector. */
private const val MIN_CALENDAR_WIDTH_DP = 360
