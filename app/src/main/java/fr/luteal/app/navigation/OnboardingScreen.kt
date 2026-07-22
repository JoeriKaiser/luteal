package fr.luteal.app.navigation

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
import fr.luteal.core.model.UserRole

@Composable
fun OnboardingScreen(
    onComplete: (role: UserRole, disorderTracking: Map<String, Boolean>) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedRole by remember { mutableStateOf(UserRole.PRIMARY_TRACKER) }
    val focusMap = remember {
        mutableStateMapOf(
            "pms" to false,
            "pmdd" to false,
            "endometriosis" to false,
            "pcos" to false
        )
    }

    val totalSteps = 4

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
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
                    Spacer(modifier = Modifier.height(LutealSpacing.md))

                    when (step) {
                        0 -> WelcomeStep()
                        1 -> RoleStep(selectedRole) { selectedRole = it }
                        2 -> FocusStep(focusMap) { key, value -> focusMap[key] = value }
                        3 -> PrivacySummaryStep()
                    }
                }

                Spacer(modifier = Modifier.height(LutealSpacing.xl))

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
                                onComplete(selectedRole, focusMap.toMap())
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
                                onClick = { onComplete(selectedRole, focusMap.toMap()) }
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
            "pcos" to stringResource(R.string.onboarding_focus_pcos)
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
