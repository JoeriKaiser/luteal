package fr.luteal.core.data.report

import fr.luteal.core.model.ClinicalReportData
import fr.luteal.core.model.ReportLanguage
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

object HtmlReportBuilder {

    fun buildHtml(data: ClinicalReportData): String {
        val isFr = data.config.language == ReportLanguage.FRENCH
        val dateFormatter = if (isFr) {
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
        } else {
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
        }

        val formattedStart = data.dateRangeStart.format(dateFormatter)
        val formattedEnd = data.dateRangeEnd.format(dateFormatter)
        val formattedGenerated = data.generatedAt.format(dateFormatter)

        val title = if (isFr) "Récapitulatif de consultation médicale" else "Clinical Consultation Summary Report"
        val periodLabel = if (isFr) "Période d'observation" else "Observation period"
        val generatedLabel = if (isFr) "Document émis le" else "Generated on"

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"${if (isFr) "fr" else "en"}\">")
            appendLine("<head>")
            appendLine("<meta charset=\"UTF-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("<title>Luteal — $title</title>")
            appendLine("<style>")
            appendLine(cssStyles())
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")

            // Header
            appendLine("<header>")
            appendLine("  <div class=\"brand\">LUTEAL</div>")
            appendLine("  <h1>$title</h1>")
            appendLine("  <p class=\"meta\"><strong>$periodLabel :</strong> $formattedStart &ndash; $formattedEnd &nbsp;|&nbsp; <strong>$generatedLabel :</strong> $formattedGenerated</p>")
            appendLine("</header>")

            // Section 1: Cycle Statistics
            appendLine("<section>")
            appendLine("  <h2>${if (isFr) "1. Synthèse des cycles" else "1. Cycle Overview"}</h2>")
            val stats = data.cycleStats
            appendLine("  <div class=\"metrics-grid\">")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Total cycles" else "Total cycles"}</span><span class=\"value\">${stats.totalCyclesCount}</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Durée moyenne" else "Mean length"}</span><span class=\"value\">${stats.meanLengthDays?.let { "%.1f j".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Médiane" else "Median"}</span><span class=\"value\">${stats.medianLengthDays?.let { "%.1f j".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Écart-type (s)" else "Std Dev (s)"}</span><span class=\"value\">${stats.sampleStdDevDays?.let { "± %.1f j".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Étendue (Min - Max)" else "Range (Min - Max)"}</span><span class=\"value\">${stats.minLengthDays ?: "--"} j &ndash; ${stats.maxLengthDays ?: "--"} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Durée moy. règles" else "Mean bleeding"}</span><span class=\"value\">${stats.meanBleedingDays?.let { "%.1f j".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("  </div>")

            if (stats.cycles.isNotEmpty()) {
                appendLine("  <table>")
                appendLine("    <thead>")
                appendLine("      <tr>")
                appendLine("        <th>${if (isFr) "Début" else "Start"}</th>")
                appendLine("        <th>${if (isFr) "Fin" else "End"}</th>")
                appendLine("        <th>${if (isFr) "Durée" else "Length"}</th>")
                appendLine("        <th>${if (isFr) "Règles" else "Bleeding"}</th>")
                appendLine("        <th>${if (isFr) "Flux max" else "Peak flow"}</th>")
                appendLine("        <th>${if (isFr) "Jours douleur" else "Pain days"}</th>")
                appendLine("        <th>${if (isFr) "Statut" else "Status"}</th>")
                appendLine("      </tr>")
                appendLine("    </thead>")
                appendLine("    <tbody>")
                for (cycle in stats.cycles) {
                    val statusText = if (cycle.isExcluded) {
                        if (isFr) "Exclu (${cycle.exclusionReason?.name ?: ""})" else "Excluded"
                    } else {
                        if (isFr) "Inclus" else "Included"
                    }
                    appendLine("      <tr>")
                    appendLine("        <td>${cycle.startDate}</td>")
                    appendLine("        <td>${cycle.endDate ?: "--"}</td>")
                    appendLine("        <td>${cycle.lengthDays} j</td>")
                    appendLine("        <td>${cycle.bleedingDaysCount} j</td>")
                    appendLine("        <td>${cycle.peakFlow.name}</td>")
                    appendLine("        <td>${cycle.painDaysCount} j</td>")
                    appendLine("        <td>$statusText</td>")
                    appendLine("      </tr>")
                }
                appendLine("    </tbody>")
                appendLine("  </table>")
            }
            appendLine("</section>")

            // Section 2: Bleeding Profile
            appendLine("<section>")
            appendLine("  <h2>${if (isFr) "2. Profil des saignements" else "2. Bleeding Profile"}</h2>")
            val b = data.bleedingDist
            appendLine("  <div class=\"metrics-grid\">")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Total jours saignement" else "Total bleeding days"}</span><span class=\"value\">${b.totalBleedingDays} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Spotting / Traces" else "Spotting"}</span><span class=\"value\">${b.spottingDays} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Flux léger" else "Light flow"}</span><span class=\"value\">${b.lightDays} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Flux moyen" else "Medium flow"}</span><span class=\"value\">${b.mediumDays} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Flux abondant" else "Heavy flow"}</span><span class=\"value\">${b.heavyDays} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Saignements intermenstruels" else "Intermenstrual bleeding"}</span><span class=\"value\">${b.intermenstrualBleedingDays} j</span></div>")
            appendLine("  </div>")
            appendLine("</section>")

            // Section 3: Pain Dynamics
            appendLine("<section>")
            appendLine("  <h2>${if (isFr) "3. Évaluation des douleurs" else "3. Pain Dynamics"}</h2>")
            val p = data.painDist
            appendLine("  <div class=\"metrics-grid\">")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Total jours avec douleur" else "Total pain days"}</span><span class=\"value\">${p.totalPainDays} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Douleurs menstruelles (dysménorrhée)" else "Menstrual pain (dysmenorrhea)"}</span><span class=\"value\">${p.dysmenorrheaDays} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Douleurs hors règles" else "Non-menstrual pelvic pain"}</span><span class=\"value\">${p.nonMenstrualPainDays} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Douleurs sévères (≥ 4/5)" else "Severe pain days (≥ 4/5)"}</span><span class=\"value\">${p.severePainDays} j</span></div>")
            appendLine("    <div class=\"metric\"><span class=\"label\">${if (isFr) "Score moyen (sur 5)" else "Mean score (/5)"}</span><span class=\"value\">${p.meanPainScore?.let { "%.1f / 5".format(Locale.US, it) } ?: "--"}</span></div>")
            appendLine("  </div>")
            appendLine("</section>")

            // Section 4: Symptom Frequencies
            if (data.symptomFrequencies.isNotEmpty()) {
                appendLine("<section>")
                appendLine("  <h2>${if (isFr) "4. Fréquence des symptômes enregistrés" else "4. Logged Symptom Frequency"}</h2>")
                appendLine("  <table>")
                appendLine("    <thead>")
                appendLine("      <tr>")
                appendLine("        <th>${if (isFr) "Symptôme" else "Symptom"}</th>")
                appendLine("        <th>${if (isFr) "Total occurrences" else "Total occurrences"}</th>")
                appendLine("        <th>${if (isFr) "En phase de règles" else "During menses"}</th>")
                appendLine("        <th>${if (isFr) "Hors règles / Lutéale" else "Outside menses"}</th>")
                appendLine("      </tr>")
                appendLine("    </thead>")
                appendLine("    <tbody>")
                for (sym in data.symptomFrequencies) {
                    val name = if (isFr) sym.symptomNameFr else sym.symptomNameEn
                    appendLine("      <tr>")
                    appendLine("        <td><strong>$name</strong></td>")
                    appendLine("        <td>${sym.totalOccurrences} j</td>")
                    appendLine("        <td>${sym.menstrualOccurrences} j</td>")
                    appendLine("        <td>${sym.nonMenstrualOccurrences} j</td>")
                    appendLine("      </tr>")
                }
                appendLine("    </tbody>")
                appendLine("  </table>")
                appendLine("</section>")
            }

            // Section 5: Notes & Medications
            if (data.notes.isNotEmpty()) {
                appendLine("<section>")
                appendLine("  <h2>${if (isFr) "5. Notes et traitements" else "5. Notes & Medications"}</h2>")
                appendLine("  <table>")
                appendLine("    <thead>")
                appendLine("      <tr>")
                appendLine("        <th>${if (isFr) "Date" else "Date"}</th>")
                appendLine("        <th>${if (isFr) "Flux" else "Flow"}</th>")
                appendLine("        <th>${if (isFr) "Douleur" else "Pain"}</th>")
                appendLine("        <th>${if (isFr) "Notes" else "Notes"}</th>")
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
            val disclaimer = if (isFr) {
                "Document descriptif généré localement par l'application Luteal à partir des données saisies par la personne. Ne constitue pas un diagnostic médical."
            } else {
                "Descriptive summary generated locally by Luteal from user-recorded observations. Does not constitute a clinical diagnosis or medical interpretation."
            }
            appendLine("  <p class=\"disclaimer\"><strong>Note :</strong> $disclaimer</p>")
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
