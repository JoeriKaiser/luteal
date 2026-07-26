# Condition Tracking Contexts and Cycle Estimation

## Purpose

The onboarding "Centres d'attention" step lets a user declare SPM, TDPM,
endometriosis, or SOPK. Those selections are written to DataStore and read by
nothing: no screen, no calculator, no symptom catalogue consumes them. The
onboarding copy nevertheless promises the selection adapts the observation
categories.

This document records what the evidence supports before that promise is made
good, and separates what can be implemented now from what cannot.

**Status: research complete; recommendations 1-5 implemented.**

Implemented: age-band prior ([AgeBand]), the STRAW+10 variability trigger, the
raised radius cap, the declared-timing-context variance floor, and the
observation catalogue ([ObservationCatalog]). Perimenopause and thyroid were
added to onboarding as TIMING contexts.

One correction found during implementation: recommendation 3 originally said to
lower `PRIOR_WEIGHT` for declared timing contexts. That inverts when the user's
own variance is *below* the prior - a person declaring SOPK with regular
recorded cycles got a narrower window than someone declaring nothing. It is
implemented as a variance floor instead, which can only widen.

## The rule this work operates under

A declared tracking context is a statement by the user about themselves. It is
never a diagnosis and must never be treated as one.

- **Permitted:** changing what the app *offers* (symptom vocabulary, bleeding
  and pain granularity) and how *uncertain* it says it is.
- **Not permitted:** changing the central prediction per condition, staging the
  user, or surfacing anything that reads as screening or confirmation.

Widening an uncertainty window because a user's own recorded cycles vary is
honest. Moving the predicted date because the app inferred a condition is not.

## Finding 1: conditions divide into two groups, and only one touches estimation

This is the central result. The four current options do not behave alike, and
treating them as one list is why the feature has no coherent effect to build.

**Group A - conditions that change cycle *timing*.** These justify wider
estimation uncertainty:

| Condition | Evidence |
|---|---|
| SOPK | WHO: "irregular or infrequent menstrual periods", "intermittent, unpredictable or absent periods", "the most common cause of anovulation among women globally". Apple/JAMA cohort: 50.4% of people with PCOS report irregular cycles vs 22.2% without, a 2.27x relative rate |
| Perimenopause | Largest measured effect anywhere in this review: within-person SD 11.19 days above age 50 vs 3.79 at 35-39 |
| Thyroid dysfunction | Cureus 2024 (n=593): oligomenorrhea 26% in overt hypothyroidism vs 12% in controls; hypermenorrhea 33% vs 6% (p=0.001) |
| Primary ovarian insufficiency | Ovarian function ceases before ~40; cycles become highly irregular or absent |
| Hypothalamic / weight / exercise-related amenorrhea | NHS names significant weight change, stress, and excessive exercise as causes of irregular periods |

**Group B - conditions that change *bleeding and pain*, not timing.** These
justify richer observation vocabulary and must **not** touch estimation:

| Condition | Evidence |
|---|---|
| Endometriosis | WHO lists severe dysmenorrhea, heavy menstrual bleeding, chronic pelvic pain. The page does not address cycle regularity at all |
| Adenomyosis | Heavy menstrual bleeding and dysmenorrhea |
| Uterine fibroids | Periods lasting "10 to 14 days rather than the usual 5 to 7 days"; blood loss up to 300-500 mL against an 80 mL abnormality threshold; affects ~25% of reproductive-age women |

Endometriosis is currently the only Group B option offered, and SOPK the only
Group A one. The step as built mixes the two without distinguishing them.

## Finding 2: correcting an earlier conclusion on endometriosis

An earlier draft of this document stated endometriosis has no established
cycle-length association. That was wrong. A meta-analysis of 11 case-control
studies (3392 cases, 5006 controls) reports:

- Cycle length <=27 days: OR **1.22** (95% CI 1.05-1.43) for endometriosis
- Cycle length >=29 days: OR **0.68** (95% CI 0.48-0.96)

**But the direction matters and rules out the obvious use.** These are
case-control studies measuring endometriosis *risk given* cycle length, in
support of the menstrual reflux hypothesis. They do not show that endometriosis
*causes* cycles to become shorter or less predictable. Using them to adjust an
estimate would invert the inference and would also mean the app implicitly
treats short cycles as a risk signal, which is screening behaviour and out of
bounds.

So the conclusion stands even though the premise was wrong: endometriosis
belongs in Group B and must not drive estimation.

## Finding 3: the 2.6-day variability prior is too low, on three independent datasets

`POPULATION_VARIATION_SD_DAYS = 2.6` (Bull et al. 2019) is applied to every
user. Two larger and more recent datasets disagree:

**Apple Women's Health Study** (Li et al. 2023, 163,275 cycles, 11,040
participants), within-person SD of cycle length:

| Group | SD (days, 95% CI) |
|---|---|
| Age 35-39 (reference) | 3.79 (3.77, 3.82) |
| Under 20 | 5.33 (5.16, 5.51) |
| Age 45-49 | 5.42 (5.27, 5.57) |
| Above 50 | 11.19 (8.94, 13.45) |

Overall: mean length 28.7 days (SD 6.1), 5th-95th percentile 22-38 days.

**Flo global cohort** (Grieger & Norman 2020, 1,579,819 women):

- Only **16.32%** of women have a median 28-day cycle
- 91.13% fall in the 21-35 day range; 8.60% exceed 35 days; 0.17% are under 21
- Of 1,086,923 women with variability data, only 25.37% vary by 0-1.5 days, and
  69% vary by under 6 days - meaning roughly **31% vary by 6 days or more**

2.6 days sits below every age band Li measured and below the variability of
about a third of Grieger's 1.09 million users. The prior dominates precisely
when history is thin, so the app is currently most confident where it has least
reason to be.

**Age is a larger driver than any condition in this review** (3.79 to 11.19
days across age bands). Luteal does not collect age. That is a product and
privacy decision, flagged not assumed.

## Finding 4: STRAW+10 gives a usable threshold that needs no condition and no age

The Stages of Reproductive Aging Workshop +10 (Harlow et al. 2012) defines
transition stages using nothing but recorded cycle lengths:

- Early menopausal transition: "increased variability in menstrual cycle length,
  defined as a persistent difference of 7 days or more in the length of
  consecutive cycles. Persistence is defined as recurrence within 10 cycles"
- Late menopausal transition: "the occurrence of amenorrhea of 60 days or longer"
- Late reproductive stage -3a: "subtle changes in menstrual cycle
  characteristics, specifically shorter cycles"

This is the most directly implementable finding in the review. The 7-day
recurrence rule operates on exactly the data Luteal already holds, is
authoritative, and can widen the estimation window **without the user declaring
anything and without the app naming a condition or stage**.

Hard constraint: Luteal must use this as an internal trigger only. Telling a
user they meet a menopausal transition criterion would be staging them, which
is diagnosis by another name.

## Finding 5: the calculator's range is already adequate

`plausibleCycleDays` is `15..90`. NHS defines irregular as under 21 or over 35
days; Grieger finds 8.60% above 35 and 0.17% below 21; STRAW's amenorrhea
threshold is 60 days. The existing range covers all of this. `IntervalsOutOfRange`
exists and is correctly worded.

No change needed. This was the obvious suspect and it is not the problem.

## Finding 6: the PCOS-specific variability constant remains unobtainable

Mortimer et al., *Variability of menstrual cycles by age, polycystic ovary
syndrome, and early-life cycle irregularity in the Apple Women's Health Study*
(AJOG, April 2026) is the one directly on-point source. The within-individual
SDs by PCOS status are in Figure 4. PubMed gives only the abstract and the
publisher full text returns HTTP 403.

Note also that Li et al. **excluded participants with PCOS history**, so it
cannot supply a SOPK prior either.

This no longer blocks implementation, because Findings 3 and 4 provide
condition-independent routes to the same behaviour.

## Recommended implementation

In priority order. None of these require a per-condition constant.

1. **Raise the general prior** from 2.6 toward the 3.79-day reference figure.
   One constant, well sourced, benefits every user, no new data collected.
2. **Add a variability trigger on recorded history**, per STRAW+10: when
   consecutive recorded cycle lengths differ by 7 or more days with recurrence
   inside 10 cycles, widen the window. Data-driven, condition-free, needs no
   disclosure from the user.
3. **Lower `PRIOR_WEIGHT` for users who declare a Group A context**, so their
   own recorded history outweighs a population prior that was not built for
   them. This widens uncertainty rather than moving the estimate, and asserts
   nothing medical.
4. **Make Group B declarations adapt the observation vocabulary only** - finer
   bleeding volume and duration granularity, richer pain vocabulary. This is
   what the onboarding copy already promises and it needs no new evidence.
5. **Revisit `MAXIMUM_RANGE_RADIUS_DAYS = 14`.** With an 11.19-day SD at the top
   age band, a 95% window would want roughly +/-22 days. The cap silently
   truncates honest uncertainty for the most variable users.

## Candidate conditions to add

Perimenopause and thyroid dysfunction are the strongest additions on this
evidence, both Group A. POI, adenomyosis, fibroids, and hypothalamic amenorrhea
are supportable but lower value.

A caution on breadth: every condition added is another sensitive disclosure
collected before the user has seen any value from the app, and Finding 4 means
much of the benefit is reachable without asking at all.

## Backend implications

None for the recommendations above; the estimator is client-side and declared
contexts are local preferences. If per-context priors ever move server-side, the
declared context becomes synced health data requiring the same E2EE treatment as
observations plus a Duo sharing decision. Larger than it looks; do not assume it.

## Required follow-up

- Obtain Mortimer et al. (2026) full text for the PCOS-stratified SDs, or an
  open equivalent. Recommendations 1-4 do not depend on it.
- Decide whether Luteal collects age or age band, given the size of the age
  effect relative to anything condition-specific.
- Domain review before any of this reaches shipped copy, per SOURCE_REGISTER.md.
- French public-health sources (HAS, Sante publique France, Inserm); current
  evidence is WHO, NHS, and US/global cohorts.
