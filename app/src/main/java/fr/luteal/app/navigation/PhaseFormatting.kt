package fr.luteal.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.model.CyclePhase
import fr.luteal.core.model.CervicalMucusSensation
import fr.luteal.core.model.CervicalMucusTexture

@Composable
internal fun phaseAnchorLabel(phase: CyclePhase): String = stringResource(
    when (phase) {
        CyclePhase.MENSTRUAL -> R.string.duo_phase_menstrual_anchor
        CyclePhase.FOLLICULAR -> R.string.duo_phase_follicular_anchor
        CyclePhase.OVULATORY -> R.string.duo_phase_ovulatory_anchor
        CyclePhase.LUTEAL -> R.string.duo_phase_luteal_anchor
    }
)

@Composable
internal fun phaseCapsuleDescription(phase: CyclePhase): String = stringResource(
    when (phase) {
        CyclePhase.MENSTRUAL -> R.string.duo_capsule_menstrual_desc
        CyclePhase.FOLLICULAR -> R.string.duo_capsule_follicular_desc
        CyclePhase.OVULATORY -> R.string.duo_capsule_ovulatory_desc
        CyclePhase.LUTEAL -> R.string.duo_capsule_luteal_desc
    }
)

@Composable
internal fun symptomDisplayName(id: String): String = when (id) {
    "cramps" -> stringResource(R.string.symptom_cramps)
    "headache" -> stringResource(R.string.symptom_headache)
    "abdominal_pain" -> stringResource(R.string.symptom_abdominal_pain)
    "backache" -> stringResource(R.string.symptom_backache)
    "muscle_aches" -> stringResource(R.string.symptom_muscle_aches)
    "fatigue" -> stringResource(R.string.symptom_fatigue)
    "sleep_issue" -> stringResource(R.string.symptom_sleep_issue)
    "bloating" -> stringResource(R.string.symptom_bloating)
    "nausea" -> stringResource(R.string.symptom_nausea)
    "digestive_changes" -> stringResource(R.string.symptom_digestive_changes)
    "breast_tenderness" -> stringResource(R.string.symptom_breast_tenderness)
    "mood_changes" -> stringResource(R.string.symptom_mood_changes)
    "anxiety" -> stringResource(R.string.symptom_anxiety)
    "acne" -> stringResource(R.string.symptom_acne)
    "pelvic_pain_outside_period" -> stringResource(R.string.symptom_pelvic_pain_outside_period)
    else -> id
}

@Composable
internal fun sensationLabel(sensation: CervicalMucusSensation): String = stringResource(
    when (sensation) {
        CervicalMucusSensation.DRY -> R.string.sensation_dry
        CervicalMucusSensation.DAMP -> R.string.sensation_damp
        CervicalMucusSensation.WET -> R.string.sensation_wet
        CervicalMucusSensation.SLIPPERY -> R.string.sensation_slippery
    }
)

@Composable
internal fun textureLabel(texture: CervicalMucusTexture): String = stringResource(
    when (texture) {
        CervicalMucusTexture.STICKY -> R.string.texture_sticky
        CervicalMucusTexture.CREAMY -> R.string.texture_creamy
        CervicalMucusTexture.EGG_WHITE -> R.string.texture_egg_white
        CervicalMucusTexture.WATERY -> R.string.texture_watery
    }
)
