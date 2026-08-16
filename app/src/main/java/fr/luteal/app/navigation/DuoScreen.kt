package fr.luteal.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealCardEmphasis
import fr.luteal.core.designsystem.component.LutealEmptyState
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.StatusPill
import fr.luteal.core.designsystem.component.StatusTone
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.SupportKind
import fr.luteal.core.network.contract.models.SupportRequest
import java.time.LocalDate

/**
 * Duo: the consensual partner surface.
 *
 * Sharing is private by default, explicit, granular, visible, and reversible.
 * Under end-to-end encryption the tracker's device composes the projection,
 * applies the grants, and seals it before it leaves the phone, so an ungranted
 * field is never encrypted, never transmitted, and cannot be leaked by the
 * server. Private notes have no representation in the payload at all.
 */
@Composable
fun DuoScreen(
    onOpenSettings: () -> Unit,
    viewModel: DuoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Reload every time the Duo tab is opened. The ViewModel is Activity-scoped
    // and survives tab switches, so without this it keeps showing whatever was
    // true when it was first created - notably "synchronisation requise" after
    // the user has since registered from Settings, which only a restart cleared.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LutealSpacing.md, vertical = LutealSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
    ) {
        ScreenHeader(
            title = stringResource(R.string.duo_title),
            subtitle = stringResource(R.string.duo_subtitle)
        )

        if (state.phase != DuoPhase.NoAccount) {
            TextButton(onClick = { viewModel.refresh() }) {
                Text(text = stringResource(R.string.duo_refresh))
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(LutealSpacing.sm))
        }

        val errorMessage = state.errorResId?.let { stringResource(it) } ?: state.error
        errorMessage?.let { error ->
            LutealCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
            }
        }

        when (state.phase) {
            DuoPhase.NoAccount -> NoAccountSection(onOpenSettings = onOpenSettings)
            DuoPhase.NoLink -> NoLinkSection(
                onCreateInvitation = { viewModel.createInvitation() },
                onAcceptCode = { viewModel.acceptInvitation(it) }
            )
            DuoPhase.InvitationPending -> InvitationPendingSection(
                pairingCode = state.shareableUrl ?: state.invitation?.pairingCode,
                onCancel = { viewModel.revokeLink() }
            )
            DuoPhase.TrackerActive -> TrackerActiveSection(
                state = state,
                onToggleGrant = { field, granted -> viewModel.toggleGrant(field, granted) },
                onRevoke = { viewModel.revokeLink() },
                onSendSupport = { kind, msg -> viewModel.sendSupportRequest(kind, msg) },
                onAck = { viewModel.ackSupportRequest(it) },
                supportDraft = state.supportDraft,
                onSupportDraftChange = { viewModel.onSupportDraftChange(it) },
                isSendingSupport = state.isSendingSupport
            )
            DuoPhase.PartnerActive -> PartnerActiveSection(
                state = state,
                onSendSupport = { kind, msg -> viewModel.sendSupportRequest(kind, msg) },
                onAck = { viewModel.ackSupportRequest(it) },
                supportDraft = state.supportDraft,
                onSupportDraftChange = { viewModel.onSupportDraftChange(it) },
                isSendingSupport = state.isSendingSupport
            )
        }
    }
}

// --- No account: sync is a prerequisite -------------------------------------

@Composable
private fun NoAccountSection(onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.lg)) {
        // This was a single card telling the user to go somewhere else, with
        // no way to get there and nothing explaining what they would be
        // turning on. Duo is a first-class surface and its empty state has to
        // carry the proposition, not defer it.
        LutealEmptyState(
            title = stringResource(R.string.duo_no_account_title),
            body = stringResource(R.string.duo_no_account_body),
            actionText = stringResource(R.string.duo_no_account_action),
            onAction = onOpenSettings,
            icon = Icons.Rounded.Favorite,
            modifier = Modifier.padding(top = LutealSpacing.md)
        )
        LutealCard(
            modifier = Modifier.fillMaxWidth(),
            emphasis = LutealCardEmphasis.QUIET
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                Text(
                    text = stringResource(R.string.duo_explainer_title),
                    style = MaterialTheme.typography.titleMedium
                )
                DuoExplainerRow(
                    icon = Icons.Rounded.Tune,
                    text = stringResource(R.string.duo_explainer_sharing)
                )
                DuoExplainerRow(
                    icon = Icons.Rounded.Undo,
                    text = stringResource(R.string.duo_explainer_reversible)
                )
                DuoExplainerRow(
                    icon = Icons.Rounded.Lock,
                    text = stringResource(R.string.duo_explainer_private)
                )
            }
        }
    }
}

@Composable
private fun DuoExplainerRow(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- No link: create or accept ---------------------------------------------

@Composable
private fun NoLinkSection(
    onCreateInvitation: () -> Unit,
    onAcceptCode: (String) -> Unit
) {
    var showCodeEntry by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)) {
            Text(
                text = stringResource(R.string.duo_not_connected_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.duo_not_connected_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LutealPrimaryButton(
                text = stringResource(R.string.duo_create_invitation),
                onClick = onCreateInvitation,
                modifier = Modifier.fillMaxWidth()
            )

            if (!showCodeEntry) {
                LutealSecondaryButton(
                    text = stringResource(R.string.duo_enter_code),
                    onClick = { showCodeEntry = true },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = stringResource(R.string.duo_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.duo_code_label)) }
                )
                LutealPrimaryButton(
                    text = stringResource(R.string.duo_accept),
                    onClick = { onAcceptCode(codeInput.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = codeInput.isNotBlank()
                )
            }
        }
    }
}

// --- Invitation pending: share the link ------------------------------------

@Composable
private fun InvitationPendingSection(
    pairingCode: String?,
    onCancel: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
            Text(
                text = stringResource(R.string.duo_pending_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.duo_pending_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            pairingCode?.let { code ->
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                // The link carries the encryption key in its fragment, so it
                // must be shared whole: a bare code cannot establish a Duo.
                Text(
                    text = stringResource(R.string.duo_share_link_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LutealSecondaryButton(
                    text = stringResource(
                        if (copied) R.string.duo_code_copied else R.string.duo_code_copy
                    ),
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.duo_revoke))
            }
        }
    }
}

// --- Tracker: grants, preview, revoke ---------------------------------------

@Composable
private fun TrackerActiveSection(
    state: DuoUiState,
    onToggleGrant: (GrantField, Boolean) -> Unit,
    onRevoke: () -> Unit,
    onSendSupport: (SupportKind, String) -> Unit,
    onAck: (String) -> Unit,
    supportDraft: String,
    onSupportDraftChange: (String) -> Unit,
    isSendingSupport: Boolean
) {
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            StatusPill(
                text = stringResource(R.string.duo_connected_tracker),
                tone = StatusTone.RECORDED
            )
        }
    }

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
            Text(
                text = stringResource(R.string.duo_sharing_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.duo_sharing_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.duo_cycle_group_title),
                style = MaterialTheme.typography.titleSmall
            )
            GrantToggle(
                field = GrantField.CYCLE_DAY,
                titleRes = R.string.duo_share_cycle_day,
                descRes = R.string.duo_share_cycle_day_desc,
                state = state,
                onToggleGrant = onToggleGrant
            )
            GrantToggle(
                field = GrantField.PERIOD_ESTIMATE,
                titleRes = R.string.duo_share_estimate,
                descRes = R.string.duo_share_estimate_desc,
                state = state,
                onToggleGrant = onToggleGrant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = stringResource(R.string.duo_feelings_group_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.duo_feelings_group_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GrantToggle(
                field = GrantField.MOOD,
                titleRes = R.string.duo_share_mood,
                descRes = R.string.duo_share_mood_desc,
                state = state,
                onToggleGrant = onToggleGrant
            )
            GrantToggle(
                field = GrantField.ENERGY,
                titleRes = R.string.duo_share_energy,
                descRes = R.string.duo_share_energy_desc,
                state = state,
                onToggleGrant = onToggleGrant
            )
            GrantToggle(
                field = GrantField.SUPPORT_REQUESTS,
                titleRes = R.string.duo_share_support,
                descRes = R.string.duo_share_support_desc,
                state = state,
                onToggleGrant = onToggleGrant
            )
        }
    }

    // Exact preview of what the partner receives: rendered from the same
    // projection that was sealed and published, not a parallel code path that
    // could drift from it.
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            Text(
                text = stringResource(R.string.duo_preview_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.duo_preview_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedProjectionList(state = state)
        }
    }

    // Private notes reminder, plus an honest statement of what the server sees.
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            StatusPill(text = stringResource(R.string.private_label), tone = StatusTone.PRIVATE)
            Text(
                text = stringResource(R.string.duo_private_notes_never_shared),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.sync_transport_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    SupportThreadSection(
        requests = state.duoView?.supportRequests,
        messages = state.supportMessages,
        onSendSupport = onSendSupport,
        onAck = onAck,
        supportDraft = supportDraft,
        onSupportDraftChange = onSupportDraftChange,
        isSendingSupport = isSendingSupport
    )

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            Text(
                text = stringResource(R.string.duo_revoke_confirm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LutealSecondaryButton(
                text = stringResource(R.string.duo_revoke),
                onClick = onRevoke,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GrantToggle(
    field: GrantField,
    titleRes: Int,
    descRes: Int,
    state: DuoUiState,
    onToggleGrant: (GrantField, Boolean) -> Unit
) {
    // Default off: sharing is opt-in, never assumed.
    val checked = state.grants[field] ?: false
    val title = stringResource(titleRes)
    val description = stringResource(descRes)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = "$title. $description" },
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggleGrant(field, it) }
        )
    }
}

// --- Partner: the received projection ---------------------------------------

@Composable
private fun PartnerActiveSection(
    state: DuoUiState,
    onSendSupport: (SupportKind, String) -> Unit,
    onAck: (String) -> Unit,
    supportDraft: String,
    onSupportDraftChange: (String) -> Unit,
    isSendingSupport: Boolean
) {
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            StatusPill(
                text = stringResource(R.string.duo_connected_partner),
                tone = StatusTone.RECORDED
            )
        }
    }

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            Text(
                text = stringResource(R.string.duo_grants_title),
                style = MaterialTheme.typography.titleMedium
            )
            SharedProjectionList(state = state)
        }
    }

    SupportThreadSection(
        requests = state.duoView?.supportRequests,
        messages = state.supportMessages,
        onSendSupport = onSendSupport,
        onAck = onAck,
        supportDraft = supportDraft,
        onSupportDraftChange = onSupportDraftChange,
        isSendingSupport = isSendingSupport
    )
}

/**
 * Renders the decrypted projection. Shared by the tracker's preview and the
 * partner's view so the preview cannot drift from what is actually delivered.
 */
@Composable
private fun SharedProjectionList(state: DuoUiState) {
    val projection = state.projection
    val locale = LocalConfiguration.current.locales[0]

    when {
        // The link key lives only on the paired devices. Without it the payload
        // cannot be read here and the Duo has to be re-paired.
        state.keyMissing -> Text(
            text = stringResource(R.string.duo_key_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        projection == null -> Text(
            text = stringResource(R.string.duo_view_hidden),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        else -> {
            val items = buildList {
                projection.cycleDay?.let {
                    add(stringResource(R.string.duo_view_cycle_day, it))
                }
                projection.periodEstimate?.let {
                    add(
                        stringResource(
                            R.string.duo_view_estimate,
                            LocalizedDateFormatter.formatShortDate(LocalDate.parse(it.windowStart), locale),
                            LocalizedDateFormatter.formatShortDate(LocalDate.parse(it.windowEnd), locale)
                        )
                    )
                }
                projection.mood?.let {
                    add(stringResource(R.string.duo_view_mood, it.level))
                }
                projection.energy?.let {
                    add(stringResource(R.string.duo_view_energy, it.level))
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.duo_view_hidden),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(text = item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// --- Support thread ---------------------------------------------------------

@Composable
private fun SupportThreadSection(
    requests: List<SupportRequest>?,
    messages: Map<String, String>,
    onSendSupport: (SupportKind, String) -> Unit,
    onAck: (String) -> Unit,
    supportDraft: String,
    onSupportDraftChange: (String) -> Unit,
    isSendingSupport: Boolean
) {
    val locale = LocalConfiguration.current.locales[0]
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
            Text(
                text = stringResource(R.string.duo_support_title),
                style = MaterialTheme.typography.titleMedium
            )

            if (requests.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.duo_support_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                requests.forEach { request ->
                    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Falls back to the kind label when the message
                            // cannot be decrypted: kind is plaintext routing
                            // metadata and is always available.
                            Text(
                                text = messages[request.id.toString()]
                                    ?: stringResource(
                                        when (request.kind) {
                                            SupportKind.COMFORT ->
                                                R.string.duo_support_kind_comfort
                                            SupportKind.PRACTICAL ->
                                                R.string.duo_support_kind_practical
                                            SupportKind.SPACE ->
                                                R.string.duo_support_kind_space
                                            else -> R.string.duo_support_kind_general
                                        }
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (request.acknowledgedAt == null) {
                                TextButton(onClick = { onAck(request.id.toString()) }) {
                                    Text(text = stringResource(R.string.duo_support_ack))
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.duo_support_acked),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = LocalizedDateFormatter.formatShortDate(
                                request.createdAt.toLocalDate(),
                                locale
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            OutlinedTextField(
                value = supportDraft,
                onValueChange = onSupportDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.duo_support_hint)) }
            )
            LutealPrimaryButton(
                text = stringResource(R.string.duo_support_send),
                onClick = { onSendSupport(SupportKind.GENERAL, supportDraft.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = supportDraft.isNotBlank(),
                loading = isSendingSupport
            )
        }
    }
}
