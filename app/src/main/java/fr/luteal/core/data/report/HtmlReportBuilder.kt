package fr.luteal.core.data.report

import fr.luteal.core.model.ClinicalReportData
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

object HtmlReportBuilder {

    fun buildHtml(data: ClinicalReportData): String {
        val language = data.config.language
        val strings = ReportStrings.forLanguage(language)
        val dateFormatter = DateTimeFormatter.ofPattern(strings.datePattern, language.locale)
        val u = strings.dayUnit

        val formattedStart = data.dateRangeStart.format(dateFormatter)
        val formattedEnd = data.dateRangeEnd.format(dateFormatter)
        val formattedGenerated = data.generatedAt.format(dateFormatter)

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"${language.languageTag}\">")
            appendLine("<head>")
            appendLine("<meta charset=\"UTF-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("<title>Luteal: ${strings.title}</title>")
            appendLine("<style>")
            appendLine(cssStyles())
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")

            // Header
            appendLine("<header>")
            appendLine("  <div class=\"brand\">LUTEAL</div>")
            appendLine("  <h1>${strings.title}</h1>")
            appendLine("  <p class=\"meta\"><strong>${strings.htmlPeriodLabel}${strings.colonSeparator}</strong> $formattedStart &ndash; $formattedEnd &nbsp;|&nbsp; <strong>${strings.htmlGeneratedLabel}${strings.colonSeparator}</strong> $formattedGenerated</p>")
            appendLine("</header>")

            // Section 1: Cycle Statistics
            appendLine("<section>")
            appendLine("  <h2>${strings.cycleSectionHeading}</h2>")
            val stats = data.cycleStats
            appendLine("  <div class=\"metrics-grid\">")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.totalCyclesLabel}</span><span class=\"value\">${stats.totalCyclesCount}</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.meanLengthLabel}</span><span class=\"value\">${stats.meanLengthDays?.let { "%.1f $u".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.medianLabel}</span><span class=\"value\">${stats.medianLengthDays?.let { "%.1f $u".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.htmlStdDevLabel}</span><span class=\"value\">${stats.sampleStdDevDays?.let { "± %.1f $u".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.htmlRangeLabel}</span><span class=\"value\">${stats.minLengthDays ?: "--"} $u &ndash; ${stats.maxLengthDays ?: "--"} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.htmlMeanBleedingLabel}</span><span class=\"value\">${stats.meanBleedingDays?.let { "%.1f $u".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("  </div>")

            if (stats.cycles.isNotEmpty()) {
                appendLine("  <table>")
                appendLine("    <thead>")
                appendLine("      <tr>")
                appendLine("        <th>${strings.colStart}</th>")
                appendLine("        <th>${strings.colEnd}</th>")
                appendLine("        <th>${strings.colLength}</th>")
                appendLine("        <th>${strings.colBleeding}</th>")
                appendLine("        <th>${strings.colPeakFlow}</th>")
                appendLine("        <th>${strings.colPainDays}</th>")
                appendLine("        <th>${strings.colStatus}</th>")
                appendLine("      </tr>")
                appendLine("    </thead>")
                appendLine("    <tbody>")
                for (cycle in stats.cycles) {
                    val statusText = if (cycle.isExcluded) {
                        cycle.exclusionReason?.let { "${strings.statusExcluded} (${it.name})" } ?: strings.statusExcluded
                    } else {
                        strings.statusIncluded
                    }
                    appendLine("      <tr>")
                    appendLine("        <td>${cycle.startDate}</td>")
                    appendLine("        <td>${cycle.endDate ?: "--"}</td>")
                    appendLine("        <td>${cycle.lengthDays} $u</td>")
                    appendLine("        <td>${cycle.bleedingDaysCount} $u</td>")
                    appendLine("        <td>${cycle.peakFlow.name}</td>")
                    appendLine("        <td>${cycle.painDaysCount} $u</td>")
                    appendLine("        <td>$statusText</td>")
                    appendLine("      </tr>")
                }
                appendLine("    </tbody>")
                appendLine("  </table>")
            }
            appendLine("</section>")

            // Section 2: Bleeding Profile
            appendLine("<section>")
            appendLine("  <h2>${strings.htmlBleedingSectionHeading}</h2>")
            val b = data.bleedingDist
            appendLine("  <div class=\"metrics-grid\">")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.totalBleedingDaysLabel}</span><span class=\"value\">${b.totalBleedingDays} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.spottingFlowLabel}</span><span class=\"value\">${b.spottingDays} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.lightFlowLabel}</span><span class=\"value\">${b.lightDays} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.mediumFlowLabel}</span><span class=\"value\">${b.mediumDays} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.heavyFlowLabel}</span><span class=\"value\">${b.heavyDays} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.intermenstrualBleedingLabel}</span><span class=\"value\">${b.intermenstrualBleedingDays} $u</span></div>")
            appendLine("  </div>")
            appendLine("</section>")

            // Section 3: Pain Dynamics
            appendLine("<section>")
            appendLine("  <h2>${strings.htmlPainSectionHeading}</h2>")
            val p = data.painDist
            appendLine("  <div class=\"metrics-grid\">")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.totalPainDaysLabel}</span><span class=\"value\">${p.totalPainDays} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.menstrualPainLabel}</span><span class=\"value\">${p.dysmenorrheaDays} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.nonMenstrualPainLabel}</span><span class=\"value\">${p.nonMenstrualPainDays} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.severePainHtmlLabel}</span><span class=\"value\">${p.severePainDays} $u</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${strings.meanScoreHtmlLabel}</span><span class=\"value\">${p.meanPainScore?.let { "%.1f / 5".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("  </div>")
            appendLine("</section>")

            // Section 4: Symptom Frequencies
            if (data.symptomFrequencies.isNotEmpty()) {
                appendLine("<section>")
                appendLine("  <h2>${strings.htmlSymptomSectionHeading}</h2>")
                appendLine("  <table>")
                appendLine("    <thead>")
                appendLine("      <tr>")
                appendLine("        <th>${strings.colSymptom}</th>")
                appendLine("        <th>${strings.htmlColTotalOccurrences}</th>")
                appendLine("        <th>${strings.htmlColDuringMenses}</th>")
                appendLine("        <th>${strings.htmlColOutsideMenses}</th>")
                appendLine("      </tr>")
                appendLine("    </thead>")
                appendLine("    <tbody>")
                for (sym in data.symptomFrequencies) {
                    appendLine("      <tr>")
                    appendLine("        <td><strong>${escapeHtml(sym.symptomName)}</strong></td>")
                    appendLine("        <td>${sym.totalOccurrences} $u</td>")
                    appendLine("        <td>${sym.menstrualOccurrences} $u</td>")
                    appendLine("        <td>${sym.nonMenstrualOccurrences} $u</td>")
                    appendLine("      </tr>")
                }
                appendLine("    </tbody>")
                appendLine("  </table>")
                appendLine("</section>")
            }

            // Section 5: Notes & Medications
            if (data.notes.isNotEmpty()) {
                appendLine("<section>")
                appendLine("  <h2>${strings.htmlNotesSectionHeading}</h2>")
                appendLine("  <table>")
                appendLine("    <thead>")
                appendLine("      <tr>")
                appendLine("        <th>${strings.colDate}</th>")
                appendLine("        <th>${strings.colFlow}</th>")
                appendLine("        <th>${strings.colPain}</th>")
                appendLine("        <th>${strings.colNotes}</th>")
                appendLine("      </tr>")
                appendLine("    </thead>")
                appendLine("    <tbody>")
                for (note in data.notes) {
                    appendLine("      <tr>")
                    appendLine("        <td>${note.date}</td>")
                    appendLine("        <td>${note.bleedingIntensity?.name ?: "--"}</td>")
                    appendLine("        <td>${note.painLevel?.let { "$it/5" } ?: "--"}</td>")
                    appendLine("        <td>${escapeHtml(note.notes)}</td>")
                    appendLine("      </tr>")
                }
                appendLine("    </tbody>")
                appendLine("  </table>")
                appendLine("</section>")
            }

            // Section 6: Disclaimer
            appendLine("<footer>")
            appendLine("  <p class=\"disclaimer\"><strong>${strings.noteLabel}${strings.colonSeparator}</strong> ${strings.htmlDisclaimer}</p>")
            appendLine("</footer>")

            appendLine("</body>")
            appendLine("</html>")
        }
    }

    fun writeHtmlToStream(data: ClinicalReportData, outputStream: OutputStream) {
        val html = buildHtml(data)
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(html)
            writer.flush()
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    private fun cssStyles(): String = """
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            color: #1A1C1E;
            background: #FFFFFF;
            margin: 0;
            padding: 24px;
            font-size: 13px;
            line-height: 1.4;
        }
        header {
            border-bottom: 2px solid #235B4E;
            padding-bottom: 12px;
            margin-bottom: 20px;
        }
        .brand {
            font-size: 11px;
            font-weight: 800;
            letter-spacing: 2px;
            color: #235B4E;
        }
        h1 {
            font-size: 20px;
            margin: 4px 0;
            color: #1A1C1E;
        }
        .meta {
            font-size: 12px;
            color: #55585E;
            margin: 4px 0 0 0;
        }
        section {
            margin-bottom: 24px;
            page-break-inside: avoid;
        }
        h2 {
            font-size: 14px;
            color: #235B4E;
            border-bottom: 1px solid #E2E2E6;
            padding-bottom: 4px;
            margin: 0 0 10px 0;
        }
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
            gap: 10px;
            margin-bottom: 12px;
        }
        .metric {
            background: #F4F4F6;
            padding: 8px 12px;
            border-radius: 6px;
            border-left: 3px solid #235B4E;
        }
        .metric .label {
            display: block;
            font-size: 11px;
            color: #55585E;
        }
        .metric .value {
            display: block;
            font-size: 15px;
            font-weight: bold;
            color: #1A1C1E;
            margin-top: 2px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 12px;
            margin-top: 8px;
        }
        th, td {
            text-align: left;
            padding: 6px 8px;
            border-bottom: 1px solid #E2E2E6;
        }
        th {
            background: #F4F4F6;
            font-weight: 600;
            color: #44474E;
        }
        footer {
            margin-top: 32px;
            border-top: 1px solid #E2E2E6;
            padding-top: 12px;
        }
        .disclaimer {
            font-size: 11px;
            color: #74777F;
            font-style: italic;
        }
        @media print {
            body { padding: 0; }
            section { page-break-inside: avoid; }
        }
    """.trimIndent()
}
