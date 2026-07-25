package fr.luteal.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.core.data.repository.DuoRepository
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.core.model.DuoProjection
import fr.luteal.core.model.SharedEstimate
import fr.luteal.core.model.SharedLevel
import fr.luteal.core.network.ContractJson
import fr.luteal.core.network.crypto.DuoCrypto
import fr.luteal.core.network.crypto.DuoKeyStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import fr.luteal.core.network.contract.models.DuoLink
import fr.luteal.core.network.contract.models.DuoView
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.Invitation
import fr.luteal.core.network.contract.models.SupportKind
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DuoViewModel @Inject constructor(
    private val duoRepository: DuoRepository,
    private val duoKeyStore: DuoKeyStore,
    private val cycleRepository: CycleRepository,
    private val dailyEntryRepository: DailyEntryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuoUiState())
    val uiState: StateFlow<DuoUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (!duoRepository.hasAccount()) {
            _uiState.update { it.copy(phase = DuoPhase.NoAccount, isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { duoRepository.duoView() }
                .onSuccess { view ->
                    val isTracker =
                        view.role == fr.luteal.core.network.contract.models.DuoRole.TRACKER
                    _uiState.update {
                        it.copy(
                            phase = if (isTracker) DuoPhase.TrackerActive else DuoPhase.PartnerActive,
                            duoView = view,
                            activeLinkId = view.linkId.toString(),
                            projection = openProjection(view),
                            supportMessages = openSupportMessages(view),
                            // Read grants back from the server before
                            // republishing. Without this the tracker would seal
                            // an empty projection on every cold start and
                            // silently wipe what the partner sees.
                            grants = view.grants.orEmpty().associateWith { true },
                            keyMissing = duoKeyStore.load(view.linkId.toString()) == null,
                            isLoading = false
                        )
                    }
                    // The tracker owns the projection: refresh republishes it so
                    // the partner sees current data and revoked grants drop out.
                    if (isTracker) publishProjection()
                }
                .onFailure { err ->
                    // 404 = no active link; fall back to link discovery.
                    _uiState.update { it.copy(isLoading = false) }
                    discoverLinks()
                }
        }
    }

    private fun discoverLinks() {
        viewModelScope.launch {
            runCatching { duoRepository.listLinks() }
                .onSuccess { response ->
                    val pending = response.links.firstOrNull {
                        it.status == DuoLink.Status.PENDING
                    }
                    val active = response.links.firstOrNull {
                        it.status == DuoLink.Status.ACTIVE
                    }
                    if (active != null) {
                        runCatching { duoRepository.duoView() }
                            .onSuccess { view ->
                                _uiState.update {
                                    it.copy(
                                        phase = if (view.role == fr.luteal.core.network.contract.models.DuoRole.TRACKER)
                                            DuoPhase.TrackerActive else DuoPhase.PartnerActive,
                                        duoView = view,
                                        activeLinkId = active.id.toString()
                                    )
                                }
                            }
                            .onFailure {
                                _uiState.update {
                                    it.copy(
                                        phase = if (active.role == fr.luteal.core.network.contract.models.DuoRole.TRACKER)
                                            DuoPhase.TrackerActive else DuoPhase.PartnerActive,
                                        activeLinkId = active.id.toString()
                                    )
                                }
                            }
                    } else if (pending != null) {
                        _uiState.update {
                            it.copy(
                                phase = DuoPhase.InvitationPending,
                                activeLinkId = pending.id.toString()
                            )
                        }
                    } else {
                        _uiState.update { it.copy(phase = DuoPhase.NoLink) }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(phase = DuoPhase.NoLink) }
                }
        }
    }

    fun createInvitation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { duoRepository.createInvitation() }
                .onSuccess { invitation ->
                    // The link key is generated here and never sent to the
                    // server: it travels to the partner in the URL fragment.
                    val linkId = invitation.linkId.toString()
                    val linkKey = DuoCrypto.generateLinkKey()
                    duoKeyStore.save(linkId, linkKey)
                    _uiState.update {
                        it.copy(
                            phase = DuoPhase.InvitationPending,
                            invitation = invitation,
                            activeLinkId = linkId,
                            shareableUrl = DuoCrypto.shareableUrl(invitation.pairingUrl, linkKey),
                            isLoading = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    /**
     * Accepts a pairing link. The full link is required: the encryption key
     * lives in its fragment, so a bare pairing code cannot establish a Duo.
     */
    fun acceptInvitation(pairingLink: String) {
        val pairing = runCatching { DuoCrypto.parsePairing(pairingLink) }.getOrNull()
        if (pairing == null) {
            _uiState.update { it.copy(error = INVALID_PAIRING_LINK, isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { duoRepository.acceptLink(pairing.code) }
                .onSuccess { accepted ->
                    duoKeyStore.save(accepted.link.id.toString(), pairing.linkKey)
                    _uiState.update { it.copy(isLoading = false) }
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    fun toggleGrant(field: GrantField, granted: Boolean) {
        val linkId = _uiState.value.activeLinkId
            ?: _uiState.value.duoView?.linkId?.toString()
            ?: return
        viewModelScope.launch {
            runCatching { duoRepository.patchGrants(linkId, field, granted) }
                .onSuccess {
                    // Revocation must stop the data flowing, not just flip a
                    // server flag: republish so the field is no longer sealed in.
                    publishProjection()
                    _uiState.update { state ->
                        state.copy(grants = state.grants + (field to granted))
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message) }
                }
        }
    }

    fun revokeLink() {
        val linkId = _uiState.value.activeLinkId
            ?: _uiState.value.duoView?.linkId?.toString()
            ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { duoRepository.revokeLink(linkId) }
                .onSuccess {
                    _uiState.update {
                        DuoUiState(phase = DuoPhase.NoLink)
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    fun sendSupportRequest(kind: SupportKind, message: String) {
        val linkId = _uiState.value.activeLinkId
            ?: _uiState.value.duoView?.linkId?.toString()
            ?: return
        val key = duoKeyStore.load(linkId)
        if (key == null) {
            _uiState.update { it.copy(error = INVALID_PAIRING_LINK) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingSupport = true, error = null) }
            // Sealed before it leaves the device: the server relays support
            // messages but never reads them.
            val sealed = DuoCrypto.sealRaw(key, linkId, message.toByteArray())
            runCatching { duoRepository.createSupportRequest(linkId, kind, sealed) }
                .onSuccess {
                    _uiState.update { it.copy(isSendingSupport = false, supportDraft = "") }
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isSendingSupport = false) }
                }
        }
    }

    fun ackSupportRequest(requestId: String) {
        viewModelScope.launch {
            runCatching { duoRepository.ackSupportRequest(requestId) }
                .onSuccess { refresh() }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun onSupportDraftChange(value: String) {
        _uiState.update { it.copy(supportDraft = value) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }


    /**
     * Opens the sealed projection with the link key held on this device.
     * Returns null when there is no payload yet or the key is missing (for
     * example after a reinstall, which requires re-pairing).
     */
    private fun openProjection(view: DuoView): DuoProjection? {
        val payload = view.payload ?: return null
        val linkId = view.linkId.toString()
        val key = duoKeyStore.load(linkId) ?: return null
        return runCatching {
            val plaintext = DuoCrypto.open(key, linkId, String(payload))
            ContractJson.decodeFromString(DuoProjection.serializer(), String(plaintext))
        }.getOrNull()
    }

    /**
     * Decrypts the support thread, keyed by request id.
     *
     * A message that fails to open is omitted rather than shown as garbage:
     * the UI falls back to the request's kind label, which is plaintext
     * routing metadata and always available.
     */
    private fun openSupportMessages(view: DuoView): Map<String, String> {
        val linkId = view.linkId.toString()
        val key = duoKeyStore.load(linkId) ?: return emptyMap()
        return view.supportRequests.orEmpty().mapNotNull { request ->
            val sealed = request.messageCiphertext ?: return@mapNotNull null
            runCatching { String(DuoCrypto.openRaw(key, linkId, sealed)) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { request.id.toString() to it }
        }.toMap()
    }

    /**
     * Composes the projection from local records, applies the grants, seals it,
     * and publishes it.
     *
     * Grants are enforced here rather than server-side: an ungranted field is
     * never encrypted, so the server never receives it and cannot leak it.
     */
    fun publishProjection() {
        val linkId = _uiState.value.activeLinkId ?: return
        val key = duoKeyStore.load(linkId) ?: return
        val grants = _uiState.value.grants

        viewModelScope.launch {
            runCatching {
                val cycles = cycleRepository.getCyclesOnce()
                val today = LocalDate.now()

                val cycleDay = if (grants[GrantField.CYCLE_DAY] == true) {
                    cycles.filter { !it.startDate.isAfter(today) }
                        .maxByOrNull { it.startDate }
                        ?.let { ChronoUnit.DAYS.between(it.startDate, today).toInt() + 1 }
                } else null

                val estimate = if (grants[GrantField.PERIOD_ESTIMATE] == true) {
                    CycleEstimateCalculator.estimateNextPeriod(cycles)?.let {
                        SharedEstimate(
                            windowStart = it.earliestDate.toString(),
                            windowEnd = it.latestDate.toString()
                        )
                    }
                } else null

                val latest = dailyEntryRepository.observeEntries().first()
                    .filter { !it.date.isAfter(today) }
                    .maxByOrNull { it.date }

                val mood = if (grants[GrantField.MOOD] == true) {
                    latest?.moodLevel?.let { SharedLevel(latest.date.toString(), it) }
                } else null

                val energy = if (grants[GrantField.ENERGY] == true) {
                    latest?.energyLevel?.let { SharedLevel(latest.date.toString(), it) }
                } else null

                val projection = DuoProjection(
                    cycleDay = cycleDay,
                    periodEstimate = estimate,
                    mood = mood,
                    energy = energy
                )
                val json = ContractJson.encodeToString(DuoProjection.serializer(), projection)
                duoRepository.putDuoPayload(DuoCrypto.seal(key, linkId, json.toByteArray()))
                projection
            }.onSuccess { projection ->
                _uiState.update { it.copy(projection = projection) }
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message) }
            }
        }
    }
}

/** Shown when a pasted pairing link carries no key fragment. */
const val INVALID_PAIRING_LINK =
    "Lien de partage incomplet : utilisez le lien complet fourni par votre partenaire."

enum class DuoPhase {
    NoAccount,
    NoLink,
    InvitationPending,
    TrackerActive,
    PartnerActive
}

data class DuoUiState(
    val phase: DuoPhase = DuoPhase.NoAccount,
    val isLoading: Boolean = false,
    val error: String? = null,
    val invitation: Invitation? = null,
    val duoView: DuoView? = null,
    /** Decrypted projection; null when absent or the link key is missing. */
    val projection: DuoProjection? = null,
    /** Decrypted support messages, keyed by request id. */
    val supportMessages: Map<String, String> = emptyMap(),
    /** True when this device has no key for the link and must re-pair. */
    val keyMissing: Boolean = false,
    val activeLinkId: String? = null,
    /** Pairing URL including the key fragment; shown only to the tracker. */
    val shareableUrl: String? = null,
    val grants: Map<GrantField, Boolean> = emptyMap(),
    val supportDraft: String = "",
    val isSendingSupport: Boolean = false
)