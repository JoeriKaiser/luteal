# Research Source Register

## Related registers

Physiology, terminology, and data-model sources live in the backend register:
`~/Projects/folicular/docs/research/SOURCES.md` (with topic notes linking each
source to the schema decision it informs). This register keeps product,
content, privacy, and platform sources.

## Use

Every external source must be tied to a specific product decision. A source is not permission to reproduce clinical language or add diagnosis features. Content derived from a source requires a review date, jurisdiction note, and plain-language scope check.

## Status definitions

- **Candidate:** identified but not yet reviewed for a product decision.
- **Reviewed:** content and publication context checked by the team.
- **Implemented:** a documented product decision cites the reviewed source.
- **Retired:** no longer current or appropriate.

## Reviewed sources

All URLs below were fetched and returned HTTP 200 on **2026-07-25**. Publication
or review dates are the ones stated by the publisher on that date.

| Area | Authority | Source | Publisher date | Jurisdiction | Status | Product decision |
|---|---|---|---|---|---|---|
| Menstrual health framing | World Health Organization | https://www.who.int/news/item/22-06-2022-who-statement-on-menstrual-health-and-rights | 2022-06-22 | International | Implemented | Menstruation framed as a health matter, not hygiene. Inclusive phrasing ("people who menstruate") across onboarding and Duo copy. |
| Premenstrual observations | NHS | https://www.nhs.uk/conditions/pre-menstrual-syndrome/ | Reviewed 2024-06-18, next review 2027-06-18 | United Kingdom | Implemented | Observation vocabulary only (mood, fatigue, bloating, cramping, breast tenderness, headache, skin). Supports prospective multi-cycle diaries as clinician input, never as an in-app conclusion. |
| Endometriosis context | World Health Organization | https://www.who.int/news-room/fact-sheets/detail/endometriosis | 2025-10-15 | International | Implemented | Neutral terminology for the optional endometriosis tracking focus. Diagnosis involves clinical assessment and imaging, which is explicitly outside app scope. |
| Polycystic ovary syndrome context | World Health Organization | https://www.who.int/news-room/fact-sheets/detail/polycystic-ovary-syndrome | 2026-01-22 | International | Implemented | Neutral terminology plus the requirement that long, irregular, and absent cycles be first-class. Diagnosis needs at least two clinical criteria with exclusions, so the app must never suggest one. |
| Health-data classification | CNIL | https://www.cnil.fr/fr/sante | Undated hub page | France | Reviewed | Health-data classification and consent review. Underpins treating all tracking data as sensitive. |
| Data protection design | CNIL | https://www.cnil.fr/fr/rgpd-de-quoi-parle-t-on | Undated explainer | France | Reviewed | French privacy baseline for consent, minimisation, and transparency copy. |
| Android accessibility | Android Developers | https://developer.android.com/guide/topics/ui/accessibility | Continuously updated | Platform | Implemented | Compose semantics, focus order, and the WCAG 2.2 AA target in AGENTS.md. |
| Android backup | Android Developers | https://developer.android.com/identity/data/autobackup | Continuously updated | Platform | Implemented | `allowBackup="false"` plus blanket excludes in `backup_rules.xml` and `data_extraction_rules.xml`. |

### Cycle variability evidence

| Area | Source | Date | Status | Product decision |
|---|---|---|---|---|
| Within-person cycle length variation | Bull et al., *Real-world menstrual cycle characteristics of more than 600,000 menstrual cycles*, npj Digital Medicine. https://pmc.ncbi.nlm.nih.gov/articles/PMC6710244/ | 2019 | Implemented | Mean cycle length 29.3 days (SD 5.2); mean **per-user** variation 2.6 days (SD 2.5) across 612,613 cycles. Used directly as `POPULATION_VARIATION_SD_DAYS` in `CycleEstimateCalculator`, replacing a range/2 heuristic that understated uncertainty when history was thinnest. Also the evidence for not modelling a canonical 28-day cycle. |

## Scope limits recorded during review

- The WHO endometriosis fact sheet states a clinical diagnosis may be made from symptoms plus imaging. That is a clinician activity. Luteal records observations and must not present any pattern as diagnostic support.
- The WHO PCOS fact sheet reports up to 70% of affected people are undiagnosed. This is a reason to make long and irregular cycles work well, not a reason to surface screening prompts.
- The NHS PMS page describes PMDD as more severe PMS. Luteal uses TDPM/SPM only as user-selected tracking contexts, never as inferred labels.

## Required follow-up

- Re-verify URLs and publisher dates at each release; NHS content carries an explicit next-review date.
- Add authoritative French public-health sources where they cover the exact decision (currently the health sources are WHO/NHS while the product ships French-first).
- Record source excerpts only when licensing and context permit.
- Obtain appropriate domain review before publishing content that could be understood as health guidance.
- The CNIL entries remain **Reviewed** rather than **Implemented**: no shipped copy yet cites a specific CNIL requirement, and a privacy notice referencing them is still outstanding.
