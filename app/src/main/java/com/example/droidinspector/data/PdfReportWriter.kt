package com.example.droidinspector.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import com.example.droidinspector.core.AuditDetail
import com.example.droidinspector.core.AuditReport
import com.example.droidinspector.core.DailyTotal
import com.example.droidinspector.core.PackageEvent
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Dónde quedó el PDF, para poder avisar al usuario. */
data class WrittenReport(val uri: Uri, val displayName: String)

/**
 * Escribe el reporte como PDF en la carpeta Descargas. Usa `PdfDocument` del framework
 * y `MediaStore`, que desde Android 10 no exige permiso de almacenamiento.
 */
class PdfReportWriter(private val context: Context) {

    fun write(report: AuditReport, labels: Map<String, AppLabel>): WrittenReport {
        val document = PdfDocument()
        try {
            Painter(document, context, labels).draw(report)
            return save(document, fileName())
        } finally {
            document.close()
        }
    }

    private fun save(document: PdfDocument, displayName: String): WrittenReport {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: throw IOException("Descargas no aceptó el archivo")

        resolver.openOutputStream(uri)?.use(document::writeTo)
            ?: throw IOException("No se pudo escribir en Descargas")

        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return WrittenReport(uri, displayName)
    }

    private fun fileName(): String =
        "auditoria-uso-${FILE_STAMP.format(Instant.now().atZone(ZoneId.systemDefault()))}.pdf"

    /** Dibuja el contenido, abriendo una página nueva cuando se acaba el alto útil. */
    private class Painter(
        private val document: PdfDocument,
        private val context: Context,
        private val labels: Map<String, AppLabel>,
    ) {
        private val zone = ZoneId.systemDefault()
        private val title = TextPaint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
        private val heading = TextPaint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        private val body = TextPaint().apply { textSize = 11f }
        private val muted = TextPaint().apply { textSize = 9f; color = 0xFF666666.toInt() }

        private var page: PdfDocument.Page? = null
        private var cursorY = 0f
        private var pageNumber = 0

        fun draw(report: AuditReport) {
            startPage()
            text(context.getString(com.example.droidinspector.R.string.pdf_title), title)
            space(6f)
            text(rangeLine(report), muted)
            text(deviceLine(), muted)
            space(18f)

            text(context.getString(com.example.droidinspector.R.string.pdf_summary), heading)
            space(6f)
            text(summaryLine(report), body)
            space(18f)

            text(context.getString(com.example.droidinspector.R.string.dashboard_apps_title), heading)
            space(8f)
            val detail = report.detail as? AuditDetail.Detailed
            report.appUsages.forEach { usage ->
                val name = labels[usage.packageName]?.label ?: usage.packageName
                // Sin eventos finos no hay aperturas: la columna va vacía, no a cero
                val launches = detail?.perApp?.get(usage.packageName)?.launchCount
                row(name, launches?.toString().orEmpty(), formatDuration(usage.totalMillis))
            }
            if (report.appUsages.isEmpty()) {
                text(context.getString(com.example.droidinspector.R.string.dashboard_empty), body)
            }
            space(4f)
            text(context.getString(com.example.droidinspector.R.string.pdf_excluded_note), muted)

            if (report.dailyTotals.isNotEmpty()) {
                space(18f)
                text(context.getString(com.example.droidinspector.R.string.pdf_daily), heading)
                space(8f)
                dailyTable(report.dailyTotals)
            }

            space(18f)
            text(context.getString(com.example.droidinspector.R.string.pdf_installs), heading)
            space(8f)
            installTable(report.installEvents)

            finishPage()
        }

        /** APP · FECHA · HORA · ACTIVIDAD. No hay desinstalaciones: Android no las guarda. */
        private fun installTable(events: List<PackageEvent>) {
            if (events.isEmpty()) {
                text(context.getString(com.example.droidinspector.R.string.pdf_installs_empty), body)
                return
            }
            installRow(
                context.getString(com.example.droidinspector.R.string.pdf_install_app),
                context.getString(com.example.droidinspector.R.string.pdf_install_date),
                context.getString(com.example.droidinspector.R.string.pdf_install_time),
                context.getString(com.example.droidinspector.R.string.pdf_install_activity),
                heading,
            )
            events.forEach { event ->
                val when_ = Instant.ofEpochMilli(event.timestampMillis).atZone(zone)
                val activity = context.getString(
                    when (event.type) {
                        PackageEvent.Type.INSTALL -> com.example.droidinspector.R.string.pdf_install_installed
                        PackageEvent.Type.UPDATE -> com.example.droidinspector.R.string.pdf_install_updated
                        PackageEvent.Type.UNINSTALL -> com.example.droidinspector.R.string.pdf_install_uninstalled
                    },
                )
                installRow(
                    labels[event.packageName]?.label ?: event.packageName,
                    INSTALL_DATE.format(when_),
                    INSTALL_TIME.format(when_),
                    activity,
                    body,
                )
            }
            space(4f)
            text(context.getString(com.example.droidinspector.R.string.pdf_installs_note), muted)
        }

        private fun installRow(app: String, date: String, time: String, activity: String, paint: TextPaint) {
            val lineHeight = paint.textSize * 1.6f
            val target = canvas(lineHeight)
            val baseline = cursorY + paint.textSize
            val clipped = TextUtils.ellipsize(app, paint, INSTALL_APP_WIDTH, TextUtils.TruncateAt.END).toString()
            target.drawText(clipped, MARGIN, baseline, paint)
            target.drawText(date, INSTALL_DATE_COLUMN, baseline, paint)
            target.drawText(time, INSTALL_TIME_COLUMN, baseline, paint)
            target.drawText(activity, INSTALL_ACTIVITY_COLUMN, baseline, paint)
            cursorY += lineHeight
        }

        private fun dailyTable(days: List<DailyTotal>) {
            tableRow(
                context.getString(com.example.droidinspector.R.string.pdf_day_date),
                context.getString(com.example.droidinspector.R.string.pdf_day_time),
                context.getString(com.example.droidinspector.R.string.pdf_day_span),
                heading,
            )
            days.forEach { day ->
                val time = formatDuration(day.totalMillis) +
                    if (day.estimated) " " + context.getString(com.example.droidinspector.R.string.pdf_day_estimated_mark) else ""
                tableRow(DAY_FORMAT.format(day.date), time, spanOf(day), body)
                // Con eventos finos, debajo del día van sus apps más usadas
                day.perApp.take(DAILY_APPS_LIMIT).forEach { app ->
                    val name = labels[app.packageName]?.label ?: app.packageName
                    appOfDayRow(name, formatDuration(app.totalMillis))
                }
            }
            if (days.any { it.estimated }) {
                space(4f)
                text(context.getString(com.example.droidinspector.R.string.pdf_day_estimated_note), muted)
            }
        }

        /** App listada bajo su día: sangrada y más tenue que la fila del día. */
        private fun appOfDayRow(name: String, time: String) {
            val lineHeight = muted.textSize * 1.5f
            val target = canvas(lineHeight)
            val baseline = cursorY + muted.textSize
            val clipped = TextUtils.ellipsize(name, muted, DAY_COLUMN_WIDTH - DAILY_APP_INDENT, TextUtils.TruncateAt.END).toString()
            target.drawText(clipped, MARGIN + DAILY_APP_INDENT, baseline, muted)
            target.drawText(time, DAY_TIME_COLUMN, baseline, muted)
            cursorY += lineHeight
        }

        /** Franja real "HH:mm–HH:mm"; en modo estimado no existe, va un guion. */
        private fun spanOf(day: DailyTotal): String {
            val first = day.firstActivityMillis
            val last = day.lastActivityMillis
            if (first == null || last == null) return "—"
            return TIME_OF_DAY.format(Instant.ofEpochMilli(first).atZone(zone)) + "–" +
                TIME_OF_DAY.format(Instant.ofEpochMilli(last).atZone(zone))
        }

        private fun tableRow(date: String, time: String, span: String, paint: TextPaint) {
            val lineHeight = paint.textSize * 1.6f
            val target = canvas(lineHeight)
            val baseline = cursorY + paint.textSize
            val clipped = TextUtils.ellipsize(date, paint, DAY_COLUMN_WIDTH, TextUtils.TruncateAt.END).toString()
            target.drawText(clipped, MARGIN, baseline, paint)
            target.drawText(time, DAY_TIME_COLUMN, baseline, paint)
            target.drawText(span, DAY_SPAN_COLUMN, baseline, paint)
            cursorY += lineHeight
        }

        /** En modo resumido se declaran los dos rangos: lo pedido y lo que de verdad cubre. */
        private fun rangeLine(report: AuditReport): String {
            val summarized = report.detail as? AuditDetail.Summarized
            val shown = summarized?.effectiveRange ?: report.range
            val base = context.getString(
                com.example.droidinspector.R.string.pdf_range,
                DATE_TIME.format(Instant.ofEpochMilli(shown.startMillis).atZone(zone)),
                DATE_TIME.format(Instant.ofEpochMilli(shown.endMillis).atZone(zone)),
            )
            if (summarized == null) return base
            return base + " · " + context.getString(
                com.example.droidinspector.R.string.pdf_requested_range,
                DATE_TIME.format(Instant.ofEpochMilli(report.range.startMillis).atZone(zone)),
                DATE_TIME.format(Instant.ofEpochMilli(report.range.endMillis).atZone(zone)),
            )
        }

        private fun deviceLine(): String = context.getString(
            com.example.droidinspector.R.string.pdf_device,
            "${Build.MANUFACTURER} ${Build.MODEL}",
            Build.VERSION.RELEASE,
            DATE_TIME.format(Instant.now().atZone(zone)),
        )

        private fun summaryLine(report: AuditReport): String {
            val detail = report.detail
            return if (detail is AuditDetail.Detailed) {
                context.getString(
                    com.example.droidinspector.R.string.pdf_summary_values,
                    formatDuration(report.totalUsageMillis),
                    report.appUsages.size,
                    detail.perApp.values.sumOf { it.launchCount },
                )
            } else {
                context.getString(
                    com.example.droidinspector.R.string.pdf_summary_values_foreground,
                    formatDuration(report.totalUsageMillis),
                    report.appUsages.size,
                )
            }
        }

        private fun startPage() {
            pageNumber += 1
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
            )
            cursorY = MARGIN
        }

        private fun finishPage() {
            page?.let { current ->
                current.canvas.drawText(
                    pageNumber.toString(),
                    (PAGE_WIDTH - MARGIN),
                    (PAGE_HEIGHT - MARGIN / 2),
                    muted,
                )
                document.finishPage(current)
            }
            page = null
        }

        private fun canvas(neededHeight: Float): Canvas {
            if (cursorY + neededHeight > PAGE_HEIGHT - MARGIN) {
                finishPage()
                startPage()
            }
            return requireNotNull(page).canvas
        }

        private fun text(value: String, paint: Paint) {
            val lineHeight = paint.textSize * 1.4f
            canvas(lineHeight).drawText(value, MARGIN, cursorY + paint.textSize, paint)
            cursorY += lineHeight
        }

        private fun row(name: String, launches: String, duration: String) {
            val lineHeight = body.textSize * 1.6f
            val target = canvas(lineHeight)
            val baseline = cursorY + body.textSize
            val clipped = TextUtils.ellipsize(
                name, body, NAME_COLUMN_WIDTH, TextUtils.TruncateAt.END,
            ).toString()
            target.drawText(clipped, MARGIN, baseline, body)
            target.drawText(launches, LAUNCHES_COLUMN, baseline, body)
            target.drawText(duration, DURATION_COLUMN, baseline, body)
            cursorY += lineHeight
        }

        private fun space(height: Float) {
            cursorY += height
        }

        /** Duplicado mínimo del formato de `ui`: `data` no debe depender de la capa de arriba. */
        private fun formatDuration(millis: Long): String {
            val totalMinutes = millis / 60_000
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}min"
                minutes > 0 -> "${minutes}min"
                else -> "<1min"
            }
        }
    }

    private companion object {
        // A4 en puntos
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val NAME_COLUMN_WIDTH = 330f
        const val LAUNCHES_COLUMN = 400f
        const val DURATION_COLUMN = 480f

        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM")
        val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        const val DAY_COLUMN_WIDTH = 230f
        const val DAY_TIME_COLUMN = 290f
        const val DAY_SPAN_COLUMN = 410f
        const val DAILY_APP_INDENT = 16f
        const val DAILY_APPS_LIMIT = 5
        const val INSTALL_APP_WIDTH = 150f
        const val INSTALL_DATE_COLUMN = 210f
        const val INSTALL_TIME_COLUMN = 300f
        const val INSTALL_ACTIVITY_COLUMN = 380f
        val INSTALL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d/MM/yyyy")
        val INSTALL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    }
}
