package fr.luteal.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.model.CyclePhase

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
