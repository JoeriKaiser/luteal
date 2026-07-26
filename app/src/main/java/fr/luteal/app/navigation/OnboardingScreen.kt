package fr.luteal.app.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealCheckboxRow
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealRadioRow
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.UserRole

@Composable
fun OnboardingScreen(
    onComplete: (
        role: UserRole,
        disorderTracking: Map<String, Boolean>,
        ageBandId: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedRole by remember { mutableStateOf(UserRole.PRIMARY_TRACKER) }
    // Null is a real answer here, not an unset value: declining to give an age
    // band is offered as an explicit option.
    var selectedAgeBand by remember { mutableStateOf<AgeBand?>(null) }
    val focusMap = remember {
        mutableStateMapOf(
            "pms" to false,
            "pmdd" to false,
            "endometriosis" to false,
            "pcos" to false,
            "perimenopause" to false,
            "thyroid" to false
        )
    }

    val totalSteps = 5

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(LutealSpacing.lg),
            contentAlignment = Alignment.TopCenter
        ) {
            // Fixed header, scrolling middle, fixed actions. Scrolling the
            // whole page instead left every short step pinned to the top with
            // several hundred pixels of empty background beneath it.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
            ) {
                Column {
                    // Header step indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${step + 1} / $totalSteps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(LutealSpacing.xs))
                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(LutealSpacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(modifier = Modifier.padding(vertical = LutealSpacing.lg)) {
                        when (step) {
                            0 -> WelcomeStep()
                            1 -> RoleStep(selectedRole) { selectedRole = it }
                            2 -> FocusStep(focusMap) { key, value -> focusMap[key] = value }
                            3 -> AgeBandStep(selectedAgeBand) { selectedAgeBand = it }
                            4 -> PrivacySummaryStep()
                        }
                    }
                }

                // Bottom Action Row
                Column {
                    LutealPrimaryButton(
                        text = if (step == totalSteps - 1) {
                            stringResource(R.string.onboarding_button_start)
                        } else {
                            stringResource(R.string.onboarding_button_next)
                        },
                        onClick = {
                            if (step == totalSteps - 1) {
                                onComplete(selectedRole, focusMap.toMap(), selectedAgeBand?.id)
                            } else {
                                step++
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (step > 0) {
                        Spacer(modifier = Modifier.height(LutealSpacing.xs))
                        LutealSecondaryButton(
                            text = stringResource(R.string.onboarding_button_back),
                            onClick = { step-- },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (step < totalSteps - 1) {
                        Spacer(modifier = Modifier.height(LutealSpacing.xs))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = { onComplete(selectedRole, focusMap.toMap(), selectedAgeBand?.id) }
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_button_skip),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    LutealCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = LutealSpacing.xxs)
            )
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_step_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.onboarding_welcome_step_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RoleStep(
    currentRole: UserRole,
    onRoleSelected: (UserRole) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        Text(
            text = stringResource(R.string.onboarding_role_step_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.onboarding_role_step_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(LutealSpacing.xs))

        RoleSelectionCard(
            title = stringResource(R.string.onboarding_role_tracker_title),
            description = stringResource(R.string.onboarding_role_tracker_desc),
            selected = currentRole == UserRole.PRIMARY_TRACKER,
            onClick = { onRoleSelected(UserRole.PRIMARY_TRACKER) }
        )

        RoleSelectionCard(
            title = stringResource(R.string.onboarding_role_partner_title),
            description = stringResource(R.string.onboarding_role_partner_desc),
            selected = currentRole == UserRole.PARTNER_VIEWER,
            onClick = { onRoleSelected(UserRole.PARTNER_VIEWER) }
        )
    }
}

@Composable
private fun RoleSelectionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        LutealRadioRow(
            title = title,
            description = description,
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
private fun FocusStep(
    focusMap: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        Text(
            text = stringResource(R.string.onboarding_focus_step_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.onboarding_focus_step_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(LutealSpacing.xs))

        val focusItems = listOf(
            "pms" to stringResource(R.string.onboarding_focus_pms),
            "pmdd" to stringResource(R.string.onboarding_focus_pmdd),
            "endometriosis" to stringResource(R.string.onboarding_focus_endometriosis),
            "pcos" to stringResource(R.string.onboarding_focus_pcos),
            "perimenopause" to stringResource(R.string.onboarding_focus_perimenopause),
            "thyroid" to stringResource(R.string.onboarding_focus_thyroid)
        )

        focusItems.forEach { (key, label) ->
            val checked = focusMap[key] ?: false
            LutealCard(modifier = Modifier.fillMaxWidth()) {
                LutealCheckboxRow(
                    title = label,
                    checked = checked,
                    onCheckedChange = { onToggle(key, it) }
                )
            }
        }
    }
}

/**
 * Optional age band.
 *
 * Asked because within-person cycle variability is U-shaped in age and a single
 * population constant cannot represent that. It is the only demographic Luteal
 * collects, it never leaves the device, and declining is a first-class option
 * rather than a skipped question.
 */
@Composable
private fun AgeBandStep(
    selected: AgeBand?,
    onSelect: (AgeBand?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        Text(
            text = stringResource(R.string.onboarding_age_step_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.onboarding_age_step_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(LutealSpacing.xs))

        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            AgeBand.entries.forEach { band ->
                LutealRadioRow(
                    title = stringResource(ageBandLabel(band)),
                    selected = selected == band,
                    onClick = { onSelect(band) }
                )
            }
            LutealRadioRow(
                title = stringResource(R.string.onboarding_age_skip),
                selected = selected == null,
                onClick = { onSelect(null) }
            )
        }
    }
}

@StringRes
private fun ageBandLabel(band: AgeBand): Int = when (band) {
    AgeBand.UNDER_20 -> R.string.age_band_under_20
    AgeBand.AGE_20_24 -> R.string.age_band_20_24
    AgeBand.AGE_25_29 -> R.string.age_band_25_29
    AgeBand.AGE_30_34 -> R.string.age_band_30_34
    AgeBand.AGE_35_39 -> R.string.age_band_35_39
    AgeBand.AGE_40_44 -> R.string.age_band_40_44
    AgeBand.AGE_45_49 -> R.string.age_band_45_49
    AgeBand.AGE_50_PLUS -> R.string.age_band_50_plus
}

@Composable
private fun PrivacySummaryStep() {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        Text(
            text = stringResource(R.string.onboarding_privacy_step_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        LutealCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = LutealSpacing.xxs)
                )
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.onboarding_privacy_step_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LutealCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = LutealSpacing.xxs)
                )
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.settings_storage_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
