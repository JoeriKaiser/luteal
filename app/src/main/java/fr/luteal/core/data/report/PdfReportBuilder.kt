package fr.luteal.core.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import fr.luteal.core.model.ClinicalReportData
import fr.luteal.core.model.ReportLanguage
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
        val isFr = data.config.language == ReportLanguage.FRENCH

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

        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        var y = MARGIN + 10f

        // Brand & Header
        canvas.drawText("LUTEAL", MARGIN, y, secondaryPaint)
        y += 18f
        val title = if (isFr) "Récapitulatif de consultation médicale" else "Clinical Consultation Summary Report"
        canvas.drawText(title, MARGIN, y, titlePaint)
        y += 14f

        val dateFormatter = if (isFr) {
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
        } else {
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
        }
        val periodText = "${if (isFr) "Période" else "Period"} : ${data.dateRangeStart.format(dateFormatter)} - ${data.dateRangeEnd.format(dateFormatter)}  |  ${if (isFr) "Émis le" else "Issued on"} : ${data.generatedAt.format(dateFormatter)}"
        canvas.drawText(periodText, MARGIN, y, secondaryPaint)
        y += 12f
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint)
        y += 20f

        // Section 1: Cycle Statistics
        canvas.drawText(if (isFr) "1. Synthèse des cycles" else "1. Cycle Overview", MARGIN, y, headerPaint)
        y += 14f

        val stats = data.cycleStats
        val metrics = listOf(
            "${if (isFr) "Total cycles" else "Total cycles"}: ${stats.totalCyclesCount}",
            "${if (isFr) "Durée moyenne" else "Mean length"}: ${stats.meanLengthDays?.let { "%.1f j".format(Locale.US, it) } ?: "--"}",
            "${if (isFr) "Médiane" else "Median"}: ${stats.medianLengthDays?.let { "%.1f j".format(Locale.US, it) } ?: "--"}",
            "${if (isFr) "Écart-type" else "Std Dev"}: ${stats.sampleStdDevDays?.let { "± %.1f j".format(Locale.US, it) } ?: "--"}",
            "${if (isFr) "Étendue" else "Range"}: ${stats.minLengthDays ?: "--"} - ${stats.maxLengthDays ?: "--"} j",
            "${if (isFr) "Durée règles" else "Bleeding duration"}: ${stats.meanBleedingDays?.let { "%.1f j".format(Locale.US, it) } ?: "--"}"
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
        canvas.drawText(if (isFr) "2. Saignements et Douleurs" else "2. Bleeding & Pain Dynamics", MARGIN, y, headerPaint)
        y += 14f

        val b = data.bleedingDist
        val p = data.painDist
        val healthMetrics = listOf(
            "${if (isFr) "Jours de saignements" else "Bleeding days"}: ${b.totalBleedingDays} j (Spotting: ${b.spottingDays}j, Abondant: ${b.heavyDays}j)",
            "${if (isFr) "Saignements intermenstruels" else "Intermenstrual bleeding"}: ${b.intermenstrualBleedingDays} j",
            "${if (isFr) "Jours de douleur" else "Pain days"}: ${p.totalPainDays} j (Dysménorrhée: ${p.dysmenorrheaDays}j, Hors règles: ${p.nonMenstrualPainDays}j)",
            "${if (isFr) "Douleurs sévères (≥4/5)" else "Severe pain (≥4/5)"}: ${p.severePainDays} j | ${if (isFr) "Score moyen" else "Mean score"}: ${p.meanPainScore?.let { "%.1f/5".format(Locale.US, it) } ?: "--"}"
        )

        for (metric in healthMetrics) {
            canvas.drawText("• $metric", MARGIN + 4f, y, textPaint)
            y += 14f
        }
        y += 10f

        // Section 3: Symptom Frequency Matrix
        if (data.symptomFrequencies.isNotEmpty()) {
            canvas.drawText(if (isFr) "3. Fréquence des symptômes" else "3. Symptom Frequencies", MARGIN, y, headerPaint)
            y += 14f

            // Table header
            canvas.drawRect(MARGIN, y - 10f, MARGIN + CONTENT_WIDTH, y + 4f, boxPaint)
            canvas.drawText(if (isFr) "Symptôme" else "Symptom", MARGIN + 6f, y, boldPaint)
            canvas.drawText(if (isFr) "Total" else "Total", MARGIN + 220f, y, boldPaint)
            canvas.drawText(if (isFr) "Règles" else "Menses", MARGIN + 310f, y, boldPaint)
            canvas.drawText(if (isFr) "Hors règles" else "Non-menses", MARGIN + 400f, y, boldPaint)
            y += 15f

            val displaySymptoms = data.symptomFrequencies.take(8)
            for (sym in displaySymptoms) {
                val name = if (isFr) sym.symptomNameFr else sym.symptomNameEn
                canvas.drawText(name, MARGIN + 6f, y, textPaint)
                canvas.drawText("${sym.totalOccurrences} j", MARGIN + 220f, y, textPaint)
                canvas.drawText("${sym.menstrualOccurrences} j", MARGIN + 310f, y, textPaint)
                canvas.drawText("${sym.nonMenstrualOccurrences} j", MARGIN + 400f, y, textPaint)
                y += 13f
            }
            y += 10f
        }

        // Section 4: Notes (if included)
        if (data.notes.isNotEmpty()) {
            canvas.drawText(if (isFr) "4. Notes et observations" else "4. Notes & Observations", MARGIN, y, headerPaint)
            y += 14f

            val displayNotes = data.notes.take(5)
            for (note in displayNotes) {
                val truncatedNote = if (note.notes.length > 70) note.notes.take(67) + "…" else note.notes
                canvas.drawText("${note.date} : $truncatedNote", MARGIN + 4f, y, textPaint)
                y += 12f
            }
            y += 10f
        }

        // Disclaimer at footer
        val footerY = PAGE_HEIGHT - MARGIN - 15f
        canvas.drawLine(MARGIN, footerY - 5f, MARGIN + CONTENT_WIDTH, footerY - 5f, linePaint)
        val disclaimerText = if (isFr) {
            "Document purement descriptif établi à partir des observations saisies par la personne. Ne constitue pas un diagnostic médical."
        } else {
            "Descriptive document generated from user observations. Does not constitute a clinical diagnosis or interpretation."
        }
        canvas.drawText(disclaimerText, MARGIN, footerY + 8f, secondaryPaint)

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }
}
