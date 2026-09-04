package fr.luteal.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealEmptyState
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.StatusPill
import fr.luteal.core.designsystem.component.StatusTone
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.CycleExclusionReason
import fr.luteal.core.model.LongitudinalCycleItem
import fr.luteal.core.model.LongitudinalCycleStats

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CycleVariabilityVisualizer(
    stats: LongitudinalCycleStats,
    onManageExclusion: (String) -> Unit,
    onStartPeriod: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedCycleItem by remember { mutableStateOf<LongitudinalCycleItem?>(null) }

    if (stats.items.isEmpty()) {
        LutealCard(modifier = modifier.fillMaxWidth()) {
            LutealEmptyState(
                icon = Icons.AutoMirrored.Rounded.ShowChart,
                title = stringResource(R.string.variability_empty_title),
                body = stringResource(R.string.variability_empty_body),
                actionText = stringResource(R.string.action_start_period_short),
                onAction = onStartPeriod
            )
        }
        return
    }

    val maxDays = remember(stats.items) {
        maxOf(45, stats.items.maxOfOrNull { it.lengthDays } ?: 45)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
    ) {
        // Summary Metrics Card
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                Text(
                    text = stringResource(R.string.variability_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.variability_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(LutealSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
                    modifier = Modifier.padding(top = LutealSpacing.xs)
                ) {
                    Text(
                        text = stringResource(R.string.variability_stat_total, stats.totalCyclesCount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    stats.rollingMedianDays?.let { median ->
                        Text(
                            text = stringResource(R.string.variability_stat_median, median),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    stats.rollingMeanDays?.let { mean ->
                        Text(
                            text = stringResource(R.string.variability_stat_mean, mean),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (stats.excludedCyclesCount > 0) {
                        Text(
                            text = stringResource(R.string.variability_stat_excluded, stats.excludedCyclesCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.variability_reference_band),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Cycle Range Bars with visual reference zone
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            val refColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            val guideColor = MaterialTheme.colorScheme.outlineVariant
            val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val x21 = size.width * (21f / maxDays.toFloat()).coerceIn(0f, 1f)
                        val x35 = size.width * (35f / maxDays.toFloat()).coerceIn(0f, 1f)
                        drawRect(
                            color = refColor,
                            topLeft = Offset(x21, 0f),
                            size = Size((x35 - x21).coerceAtLeast(0f), size.height)
                        )
                        drawLine(
                            color = guideColor,
                            start = Offset(x21, 0f),
                            end = Offset(x21, size.height),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = guideColor,
                            start = Offset(x35, 0f),
                            end = Offset(x35, size.height),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
            ) {
                stats.items.forEach { item ->
                    CycleBarRow(
                        item = item,
                        maxDays = maxDays,
                        onClick = { selectedCycleItem = item }
                    )
                }
            }

            Spacer(Modifier.height(LutealSpacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.variability_ref_min),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.variability_reference_band),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.variability_ref_max),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Detail Bottom Sheet
    selectedCycleItem?.let { item ->
        CycleDetailBottomSheet(
            item = item,
            onDismiss = { selectedCycleItem = null },
            onManageExclusion = {
                val id = item.cycleId
                selectedCycleItem = null
                onManageExclusion(id)
            }
        )
    }
}

@Composable
private fun CycleBarRow(
    item: LongitudinalCycleItem,
    maxDays: Int,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val formattedStart = remember(item.startDate, locale) {
        LocalizedDateFormatter.formatShortDate(item.startDate, locale)
    }

    val accessibilityDesc = buildString {
        append(stringResource(R.string.variability_bar_cd, formattedStart, item.lengthDays, item.bleedingDaysCount))
        if (item.isCurrent) append(" ").append(stringResource(R.string.variability_current_badge))
        if (item.isExcluded) append(" ").append(stringResource(R.string.variability_excluded_badge))
        if (item.hasStrawSwing) append(" ").append(stringResource(R.string.variability_straw_swing_badge))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = LutealSpacing.xxs)
            .semantics { contentDescription = accessibilityDesc }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedStart,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.hasStrawSwing) {
                    StatusPill(
                        text = stringResource(R.string.variability_straw_swing_badge),
                        tone = StatusTone.ESTIMATED
                    )
                }
                if (item.isExcluded) {
                    StatusPill(
                        text = stringResource(R.string.variability_excluded_badge),
                        tone = StatusTone.LOCAL_ONLY
                    )
                }
                if (item.isCurrent) {
                    StatusPill(
                        text = stringResource(R.string.variability_current_badge),
                        tone = StatusTone.RECORDED
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Range Bar Graphic with adaptive label placement
        val barFraction = (item.lengthDays.toFloat() / maxDays.toFloat()).coerceIn(0.04f, 1f)
        val showTextInside = barFraction >= 0.40f
        val labelText = stringResource(
            R.string.variability_bar_label,
            item.lengthDays,
            item.bleedingDaysCount
        )
        val contentColor = when {
            item.isExcluded -> MaterialTheme.colorScheme.onSurfaceVariant
            item.isCurrent -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            item.isExcluded -> MaterialTheme.colorScheme.surfaceVariant
                            item.isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                    .border(
                        width = if (item.isCurrent) 1.5.dp else 1.dp,
                        color = when {
                            item.isExcluded -> MaterialTheme.colorScheme.outline
                            item.isCurrent -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = LutealSpacing.xs),
                contentAlignment = Alignment.CenterStart
            ) {
                if (showTextInside) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = contentColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (!showTextInside) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleDetailBottomSheet(
    item: LongitudinalCycleItem,
    onDismiss: () -> Unit,
    onManageExclusion: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LutealSpacing.lg, vertical = LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
        ) {
            Text(
                text = stringResource(
                    R.string.variability_detail_title,
                    LocalizedDateFormatter.formatFullDate(item.startDate, locale)
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            LutealCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.variability_cycle_days, item.lengthDays),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.variability_bleeding_days, item.bleedingDaysCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.endDate?.let { end ->
                        Text(
                            text = stringResource(
                                R.string.variability_detail_end,
                                LocalizedDateFormatter.formatFullDate(end, locale)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (item.isExcluded) {
                        Text(
                            text = stringResource(R.string.variability_status_excluded),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                        item.exclusionReason?.let { reason ->
                            Text(
                                text = stringResource(
                                    R.string.variability_exclusion_reason,
                                    exclusionReasonLabel(reason)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.variability_status_included),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            LutealSecondaryButton(
                text = stringResource(R.string.variability_action_manage_exclusion),
                onClick = onManageExclusion,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(LutealSpacing.sm))
        }
    }
}

@Composable
private fun exclusionReasonLabel(reason: CycleExclusionReason): String {
    return when (reason) {
        CycleExclusionReason.ILLNESS -> stringResource(R.string.cycle_exclusion_reason_illness)
        CycleExclusionReason.MEDICAL_TREATMENT -> stringResource(R.string.cycle_exclusion_reason_medical_treatment)
        CycleExclusionReason.CONTRACEPTION_CHANGE -> stringResource(R.string.cycle_exclusion_reason_contraception_change)
        CycleExclusionReason.STRESS_OR_TRAVEL -> stringResource(R.string.cycle_exclusion_reason_stress_or_travel)
        CycleExclusionReason.OTHER -> stringResource(R.string.cycle_exclusion_reason_other)
    }
}
