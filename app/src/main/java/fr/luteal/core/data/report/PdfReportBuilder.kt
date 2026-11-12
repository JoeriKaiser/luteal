package fr.luteal.core.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import fr.luteal.core.model.ClinicalReportData
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfReportBuilder {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2)

    fun writePdfToStream(data: ClinicalReportData, outputStream: OutputStream) {
        val document = PdfDocument()
        val language = data.config.language
        val strings = ReportStrings.forLanguage(language)

        val titlePaint = Paint().apply {
            color = Color.parseColor("#235B4E")
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#235B4E")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val boldPaint = Paint().apply {
            color = Color.parseColor("#1A1C1E")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.parseColor("#1A1C1E")
            textSize = 9.5f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val secondaryPaint = Paint().apply {
            color = Color.parseColor("#55585E")
            textSize = 8.5f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E2E6")
            strokeWidth = 1f
        }

        val boxPaint = Paint().apply {
            color = Color.parseColor("#F4F4F6")
            style = Paint.Style.FILL
        }

        val title = strings.title
        val footerY = PAGE_HEIGHT - MARGIN - 15f
        val disclaimerText = strings.pdfDisclaimer

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas

        var y = MARGIN + 10f

        fun drawFooter(c: Canvas, num: Int) {
            c.drawLine(MARGIN, footerY - 5f, MARGIN + CONTENT_WIDTH, footerY - 5f, linePaint)
            c.drawText(disclaimerText, MARGIN, footerY + 8f, secondaryPaint)
            c.drawText("$num", MARGIN + CONTENT_WIDTH - 15f, footerY + 8f, secondaryPaint)
        }

        fun drawTableHeader(c: Canvas, atY: Float) {
            c.drawRect(MARGIN, atY - 10f, MARGIN + CONTENT_WIDTH, atY + 4f, boxPaint)
            c.drawText(strings.colSymptom, MARGIN + 6f, atY, boldPaint)
            c.drawText(strings.pdfColTotal, MARGIN + 220f, atY, boldPaint)
            c.drawText(strings.pdfColMenses, MARGIN + 310f, atY, boldPaint)
            c.drawText(strings.pdfColNonMenses, MARGIN + 400f, atY, boldPaint)
        }

        fun newPage(isTableContinuation: Boolean = false) {
            drawFooter(canvas, pageNumber)
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN + 10f
            canvas.drawText("LUTEAL", MARGIN, y, secondaryPaint)
            y += 14f
            canvas.drawText(title, MARGIN, y, headerPaint)
            y += 10f
            canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint)
            y += 16f
            if (isTableContinuation) {
                drawTableHeader(canvas, y)
                y += 15f
            }
        }

        fun ensureSpace(needed: Float, isTableContinuation: Boolean = false) {
            if (y + needed > footerY - 12f) {
                newPage(isTableContinuation)
            }
        }

        // Brand & Header
        canvas.drawText("LUTEAL", MARGIN, y, secondaryPaint)
        y += 18f
        canvas.drawText(title, MARGIN, y, titlePaint)
        y += 14f

        val dateFormatter = DateTimeFormatter.ofPattern(strings.datePattern, language.locale)
        val u = strings.dayUnit
        val colon = "${strings.colonSeparator} "
        val periodText = "${strings.pdfPeriodLabel}$colon${data.dateRangeStart.format(dateFormatter)} - ${data.dateRangeEnd.format(dateFormatter)}  |  ${strings.pdfIssuedLabel}$colon${data.generatedAt.format(dateFormatter)}"
        canvas.drawText(periodText, MARGIN, y, secondaryPaint)
        y += 12f
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint)
        y += 20f

        // Section 1: Cycle Statistics
        ensureSpace(60f)
        canvas.drawText(strings.cycleSectionHeading, MARGIN, y, headerPaint)
        y += 14f

        val stats = data.cycleStats
        val metrics = listOf(
            "${strings.totalCyclesLabel}: ${stats.totalCyclesCount}",
            "${strings.meanLengthLabel}: ${stats.meanLengthDays?.let { "%.1f $u".format(Locale.US, it) } ?: "--"}",
            "${strings.medianLabel}: ${stats.medianLengthDays?.let { "%.1f $u".format(Locale.US, it) } ?: "--"}",
            "${strings.pdfStdDevLabel}: ${stats.sampleStdDevDays?.let { "± %.1f $u".format(Locale.US, it) } ?: "--"}",
            "${strings.pdfRangeLabel}: ${stats.minLengthDays ?: "--"} - ${stats.maxLengthDays ?: "--"} $u",
            "${strings.pdfBleedingDurationLabel}: ${stats.meanBleedingDays?.let { "%.1f $u".format(Locale.US, it) } ?: "--"}"
        )
        val colWidth = CONTENT_WIDTH / 3
        for (i in metrics.indices) {
            val col = i % 3
            val row = i / 3
            val xPos = MARGIN + (col * colWidth)
            val yPos = y + (row * 16f)
            canvas.drawRect(xPos, yPos - 11f, xPos + colWidth - 8f, yPos + 4f, boxPaint)
            canvas.drawText(metrics[i], xPos + 6f, yPos, textPaint)
        }
        y += (metrics.size / 3 * 16f) + 16f

        // Section 2: Bleeding & Pain summary
        ensureSpace(80f)
        canvas.drawText(strings.pdfBleedingPainSectionHeading, MARGIN, y, headerPaint)
        y += 14f

        val b = data.bleedingDist
        val p = data.painDist
        val healthMetrics = listOf(
            "${strings.pdfBleedingDaysLabel}: ${b.totalBleedingDays} $u (${strings.pdfSpottingLabel}: ${b.spottingDays}$u, ${strings.pdfHeavyLabel}: ${b.heavyDays}$u)",
            "${strings.intermenstrualBleedingLabel}: ${b.intermenstrualBleedingDays} $u",
            "${strings.pdfPainDaysLabel}: ${p.totalPainDays} $u (${strings.pdfDysmenorrheaLabel}: ${p.dysmenorrheaDays}$u, ${strings.pdfNonMenstrualLabel}: ${p.nonMenstrualPainDays}$u)",
            "${strings.pdfSeverePainLabel}: ${p.severePainDays} $u | ${strings.pdfMeanScoreLabel}: ${p.meanPainScore?.let { "%.1f/5".format(Locale.US, it) } ?: "--"}"
        )

        for (metric in healthMetrics) {
            ensureSpace(14f)
            canvas.drawText("• $metric", MARGIN + 4f, y, textPaint)
            y += 14f
        }
        y += 10f

        // Section 3: Symptom Frequency Matrix
        if (data.symptomFrequencies.isNotEmpty()) {
            ensureSpace(40f)
            canvas.drawText(strings.pdfSymptomSectionHeading, MARGIN, y, headerPaint)
            y += 14f

            drawTableHeader(canvas, y)
            y += 15f

            for (sym in data.symptomFrequencies) {
                ensureSpace(14f, isTableContinuation = true)
                canvas.drawText(sym.symptomName, MARGIN + 6f, y, textPaint)
                canvas.drawText("${sym.totalOccurrences} $u", MARGIN + 220f, y, textPaint)
                canvas.drawText("${sym.menstrualOccurrences} $u", MARGIN + 310f, y, textPaint)
                canvas.drawText("${sym.nonMenstrualOccurrences} $u", MARGIN + 400f, y, textPaint)
                y += 13f
            }
            y += 10f
        }

        // Section 4: Notes (if included)
        if (data.notes.isNotEmpty()) {
            ensureSpace(30f)
            canvas.drawText(strings.pdfNotesSectionHeading, MARGIN, y, headerPaint)
            y += 14f

            for (note in data.notes) {
                ensureSpace(14f)
                val truncatedNote = if (note.notes.length > 70) note.notes.take(67) + "…" else note.notes
                canvas.drawText("${note.date} : $truncatedNote", MARGIN + 4f, y, textPaint)
                y += 12f
            }
            y += 10f
        }

        drawFooter(canvas, pageNumber)
        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }
}
