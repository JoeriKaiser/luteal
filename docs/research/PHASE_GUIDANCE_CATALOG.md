# Phase Guidance and Condition-Aware Research Catalog

## Purpose

This document links user-facing phase guidance cards on the Today screen (`PhaseTips`) and the Duo partner screen (`PartnerPhaseTips`) to verified primary sources in `docs/research/SOURCE_REGISTER.md`.

All citations below were audited against primary publications on **2026-11-21**. Unverified attributions and non-existent systematic reviews from earlier drafts have been removed or replaced with verified primary references.

## Core principles

1. **Lived experience with clinical accuracy.** Conditions such as endometriosis, PMDD, PCOS, hypothyroidism, and perimenopause involve pain, neurochemical sensitivity, fatigue, and cycle disruption. Guidance acknowledges these realities without clinical coldness or exaggerated claims.
2. **Traceable primary sources.** Every shipped card cites a specific URL verified in `SOURCE_REGISTER.md`, and the claimed effect must match what that source actually states.
3. **Strict non-diagnostic boundaries.** Tips adapt what the app offers based on user-declared tracking contexts and recent logged observations. They never screen, diagnose, stage, or prescribe medical treatment.
4. **Honest uncertainty.** Advice focuses on practical comfort measures, pacing, boundary setting, and structured observation for optional medical consultations.

---

## Detailed Clinical Evidence Register

### 1. Menstrual Phase (Règles)

#### General dysmenorrhea and flow management
- **Warmth and local relaxation (`menstrual_warmth`):**
  - *Evidence:* Localized heat (warm shower, hot water bottle wrapped in fabric, or topical heat wrap) is recommended by NHS for period pain. A systematic review and meta-analysis of 6 RCTs (Jo & Lee 2018, using the Cochrane risk-of-bias tool) found suggestive pain-relief benefit over placebo and analgesics, while noting small trial sizes and the need for more rigorous trials; an earlier RCT (Akin et al. 2001) found continuous low-level topical heat comparable to ibuprofen 400 mg over 2 days.
  - *Sources:* NHS Period Pain (reviewed 2026-03-18); Jo & Lee (*Scientific Reports*, 2018, PMC6214933); Akin et al. (*Obstet Gynecol*, 2001, PMID 11239634).
- **Gentle movement (`menstrual_movement`):**
  - *Evidence:* Low-intensity movement such as walking, yoga, swimming, or cycling may help ease period pain.
  - *Sources:* NHS Period Pain (reviewed 2026-03-18).
- **Hydration and baseline recovery (`menstrual_hydration`):**
  - *Evidence:* Normal menstrual blood loss ranges from roughly 20 to 90 mL over 2 to 7 days; heavier loss can accompany fatigue. Maintaining steady hydration and regular meals is general supportive self-care during menstruation.
  - *Sources:* NHS Periods (reviewed 2023-01-05); FIGO System 1 (Munro et al. 2018, PMID 30198563).
- **Pain escalation and medical consultation (`menstrual_pain_support`):**
  - *Evidence:* Period pain that is worse than usual, stops daily activities, or does not respond to first-line analgesics warrants evaluation by a healthcare professional.
  - *Sources:* NHS Period Pain (reviewed 2026-03-18); HAS / CNGOF Endométriose (2017/2018); Ameli Règles douloureuses.

#### Endometriosis context in menstruation
- **Pelvic relaxation and pain guarding (`menstrual_endo_pelvic_rest`):**
  - *Evidence:* French clinical guidelines and the 2025 CNGOF / Convergences PP expert consensus recognize non-pharmacological comfort, pelvic floor relaxation, and pacing alongside medical care for endometriosis-associated pelvic pain.
  - *Sources:* HAS / CNGOF Endométriose (2017/2018); CNGOF & Convergences PP (*Gynécol Obstét Fertil Sénol*, 2025, DOI `10.1016/j.gofs.2025.06.003`).
- **Radiating pain logging (`menstrual_endo_radiating_pain`):**
  - *Evidence:* Endometriosis pain can radiate to the lower back, hips, or legs; noting pain distribution and timing provides structured information for medical consultations.
  - *Sources:* HAS / CNGOF Endométriose (2017/2018); WHO Endometriosis Fact Sheet (2025-10-15); Inserm Endométriose.
- **Fatigue and pacing (`menstrual_endo_fatigue_pacing`):**
  - *Evidence:* Endometriosis is associated with chronic pelvic pain and fatigue that affect quality of life; multidisciplinary guidelines validate pacing and recovery time.
  - *Sources:* CNGOF & Convergences PP (2025, DOI `10.1016/j.gofs.2025.06.003`); WHO Endometriosis Fact Sheet (2025-10-15).

---

### 2. Follicular Phase (Phase Folliculaire)

#### General follicular physiology
- **Natural duration variability (`follicular_varies`, `follicular_own_history`, `follicular_no_fixed_day`):**
  - *Evidence:* Follicular phase duration accounts for most cycle-length variation between people and across cycles for the same person (Mihm et al. 2011; Soumpasis et al. 2020: r=0.77 with cycle length vs r=0.37 for luteal phase). Only 16.32% of 1.58M users in Grieger & Norman (2020) had a median 28-day cycle, and day 14 accounted for 13.08% of LH-confirmed cycles.
  - *Sources:* Mihm, Gangooly & Muttukrishna (2011); Grieger & Norman (2020, PMC7381001); Soumpasis et al. (2020, PMC7164578).

#### PCOS (SOPK) context in follicular phase
- **Extended follicular phase normalization (`follicular_pcos_elongation`):**
  - *Evidence:* Irregular cycles and prolonged follicular intervals with delayed or infrequent ovulation are recognized physiological features of PCOS rather than tracking errors.
  - *Sources:* Teede et al. / Monash University & ESHRE International Evidence-based Guideline for PCOS (2023, PMC10505534); WHO PCOS Fact Sheet (2026-01-22); Inserm SOPK.
- **Physical activity (`follicular_pcos_movement`):**
  - *Evidence:* The 2023 international PCOS guideline recommends regular physical activity (aerobic and strength-building) to support general health, metabolic well-being, and quality of life in people with PCOS.
  - *Sources:* Teede et al. (2023, PMC10505534).

#### Thyroid context in follicular phase
- **Fatigue and cold sensitivity (`follicular_thyroid_fatigue`):**
  - *Evidence:* Hypothyroidism commonly causes fatigue, increased sensitivity to cold, difficulty concentrating, and irregular or heavy periods. Pacing routines and recording symptom fluctuations helps prepare for clinical follow-up.
  - *Sources:* NHS Underactive thyroid / hypothyroidism (reviewed 2025-04-28); HAS Prise en charge des dysthyroïdies chez l'adulte (2023-03). Note: Güngör Semiz & Hekimsoy (2024) covers menstrual bleeding frequency/volume in thyroid disease, not fatigue, and is no longer cited for this tip.

#### Perimenopause context in follicular phase
- **Cycle length volatility (`follicular_perimeno_fluctuation`):**
  - *Evidence:* STRAW+10 defines early menopausal transition by persistent consecutive cycle-length differences of 7 days or more, reflecting variable follicular development across cycles.
  - *Sources:* Harlow et al., STRAW+10 (*Menopause*, 2012, PMC3340903).

---

### 3. Ovulatory Phase (Transition Ovulatoire)

- **Calendar estimate limitations (`ovulatory_not_confirmed`, `ovulatory_counts_back`, `ovulatory_not_day_14`):**
  - *Evidence:* In Fehring et al. (2006; 141 women, 1,060 LH-monitored cycles), 95% of 6-day fertile windows spanned cycle days 4 to 23, only 25% of participants had all fertile days inside textbook days 10 to 17, and 42.5% had >7 days intracycle variability. Soumpasis et al. (2020; 75,981 LH-tracked cycles) observed a 10-day spread of ovulation days even among 28-day cycles. Grieger & Norman (2020) found day 14 accounted for 13.08% of 18,761 LH-confirmed cycles. NHS states ovulation typically occurs about 12 to 16 days before the next period.
  - *Sources:* Fehring, Schneider & Raviele (2006); Soumpasis et al. (2020, PMC7164578); Grieger & Norman (2020, PMC7381001); NHS Periods (reviewed 2023-01-05).
- **Cervical mucus observation (`ovulatory_hydration_mucus`):**
  - *Evidence:* Approaching ovulation, estrogen-driven changes alter the amount, look, and feel of cervical mucus (ACOG FAQ024). Copy focuses strictly on observing those physical changes without making hydration or fertility claims.
  - *Sources:* ACOG Fertility Awareness-Based Methods FAQ (FAQ024, reviewed 2026-09).

---

### 4. Luteal Phase (Phase Lutéale)

- **Prospective two-cycle diary (`luteal_diary`):**
  - *Evidence:* ACOG Clinical Practice Guideline No. 7 (2023), the ISPMD Montreal Consensus (2011), and RCOG Green-top Guideline No. 48 (2017) establish prospective daily symptom recording across at least two consecutive cycles as the clinical standard for evaluating premenstrual timing.
  - *Sources:* ACOG CPG No. 7 (2023, PMID 37973069); ISPMD Montreal Consensus (O'Brien et al. 2011, PMC4134928); RCOG GTG No. 48 (2017, PMID 27900828); NHS PMS (reviewed 2024-06-18).
- **Daily lifestyle comfort and seeking care (`luteal_daily_support`, `luteal_seek_support`, `luteal_hydration`):**
  - *Evidence:* Regular physical activity, consistent sleep habits, stress reduction, and consulting a health professional when symptoms affect daily life are standard first-line self-care and care-seeking recommendations.
  - *Sources:* NHS PMS (reviewed 2024-06-18); ACOG PMS FAQ (FAQ057, reviewed 2025-11); Inserm SPM (2021-12-10).
- **Dietary adjustments (`luteal_sodium_bloating`, `luteal_complex_carbs`, `luteal_digestive_comfort`):**
  - *Evidence:* Clinical patient guidance suggests moderating salt, sugar, and caffeine, choosing complex carbohydrates, and eating smaller, more frequent meals to help with bloating, energy stability, and digestive comfort. Note: no Cochrane systematic review establishes high-certainty RCT efficacy for sodium restriction or complex carbohydrates in PMS; these are framed strictly as gentle optional comfort measures.
  - *Sources:* ACOG PMS FAQ (FAQ057, reviewed 2025-11); Inserm SPM (2021-12-10).
- **PMDD neurochemical sensitivity validation (`luteal_pmdd_neuro_validation`):**
  - *Evidence:* Luteal progesterone is metabolized to allopregnanolone, which modulates central GABA-A receptors; in PMDD, heightened central sensitivity to normal hormonal fluctuations drives severe mood and sensory symptoms rather than an absolute hormone excess or personal weakness.
  - *Sources:* ISPMD Management Consensus (Nevatte et al. 2013, PMC3955202); ACOG CPG No. 7 (2023, PMID 37973069).
- **PMDD pacing and sleep routine (`luteal_pmdd_pacing`, `luteal_sleep_routine`):**
  - *Evidence:* Premenstrual guidelines recognize functional impairment in daily routines and recommend behavioral coping, regular sleep schedules, and stress management alongside clinical options.
  - *Sources:* ACOG CPG No. 7 (2023, PMID 37973069); ACOG PMS FAQ (FAQ057, reviewed 2025-11).
- **Endometriosis non-menstrual pelvic tension (`luteal_endo_pelvic_tension`):**
  - *Evidence:* Endometriosis can cause non-menstrual pelvic discomfort outside bleeding days; warmth and relaxation postures are non-pharmacological comfort options.
  - *Sources:* HAS / CNGOF Endométriose (2017/2018); CNGOF & Convergences PP (2025, DOI `10.1016/j.gofs.2025.06.003`).
- **Perimenopause night sweats and sleep (`luteal_perimeno_sleep`):**
  - *Evidence:* Keeping the bedroom cool, wearing lightweight/layered bedding, maintaining a regular sleep routine, and limiting triggers like late caffeine or alcohol help ease night sweats and sleep disruption.
  - *Sources:* NHS Menopause and perimenopause: things you can do (reviewed 2026-05-19); BMS Non-hormonal treatments consensus (updated 2026-08).

---

## Consensual Duo Partner Guidance Catalog

Partner tips in `PartnerPhaseTips` offer non-prescriptive, consent-first support suggestions based on what the tracker explicitly shares.

| ID | Phase / Context | Evidence & Ethical Foundation | Verified Source |
|---|---|---|---|
| `partner_menstrual_comfort` | Menstrual (General) | Offer practical warmth or help without assuming pain is present. | NHS Period Pain (2026) |
| `partner_menstrual_space` | Menstrual (General) | Respect rest preferences and ask before rearranging plans. | NHS Periods (2023) |
| `partner_menstrual_listen` | Menstrual (General) | Frame menstruation as a health matter, not a mood stereotype. | WHO Menstrual Health (2022) |
| `partner_menstrual_endo_support` | Menstrual + Endometriosis | Endometriosis flares cause significant pain and exhaustion; follow the tracker's lead on practical tasks. | CNGOF & Convergences PP (2025, DOI `10.1016/j.gofs.2025.06.003`) |
| `partner_follicular_energy_varies` | Follicular (General) | Post-menses energy and follicular length vary widely; do not assume a fixed script. | Mihm et al. (2011) |
| `partner_follicular_no_script` | Follicular (General) | Avoid phase-based personality stereotypes; rely on what was explicitly shared. | WHO Menstrual Health (2022) |
| `partner_follicular_pcos_support` | Follicular + PCOS | Longer cycles are common in PCOS; encouragement matters more than calendar dates. | Teede et al. PCOS Guideline (2023, PMC10505534) |
| `partner_ovulatory_not_certain` | Ovulatory (General) | Calendar estimates cannot confirm ovulation or fertility. | Fehring et al. (2006) |
| `partner_ovulatory_ask` | Ovulatory (General) | Cycle day numbers are not instructions; ask directly what helps. | WHO Menstrual Health (2022) |
| `partner_luteal_progesterone` | Luteal (General) | Late-cycle sleep, mood, or bloating changes occur for some people, not all, and are not a diagnosis. | NHS PMS (2024) |
| `partner_luteal_communication` | Luteal (General) | Simple check-ins and schedule flexibility help more than unsolicited advice. | NHS PMS (2024) |
| `partner_luteal_practical` | Luteal (General) | Offer concrete practical help as an option, not a prescription. | ACOG PMS FAQ (2025) |
| `partner_luteal_pmdd_space` | Luteal + PMDD | Premenstrual dysphoria reflects neurochemical sensitivity; calm validation without debating helps. | ISPMD Management Consensus (2013, PMC3955202) |
| `partner_perimeno_support` | Luteal + Perimenopause | Perimenopausal sleep, energy, and temperature shifts benefit from flexible plans and a cool environment. | NHS Menopause & Perimenopause (2026) |
