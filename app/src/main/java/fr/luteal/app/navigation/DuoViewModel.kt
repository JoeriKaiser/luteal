package fr.luteal.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.core.data.repository.DuoRepository
import fr.luteal.core.data.repository.DuoCycleProjectionCacheWriter
import fr.luteal.core.data.repository.DuoWidgetCacheRepository
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.app.widget.WidgetFreshness
import fr.luteal.core.model.CurrentCyclePhase
import fr.luteal.core.model.DuoProjection
import fr.luteal.core.model.DuoSharingField
import fr.luteal.core.model.DuoSharingPreferences
import fr.luteal.core.model.PartnerPhaseResolver
import fr.luteal.core.model.PartnerPhaseTip
import fr.luteal.core.model.PartnerPhaseTips
import fr.luteal.core.model.PhaseIndeterminateReason
import fr.luteal.core.model.SharedEstimate
import fr.luteal.core.model.SharedLevel
import fr.luteal.core.network.ContractJson
import fr.luteal.core.network.crypto.DuoCrypto
import fr.luteal.core.network.crypto.DuoKeyStore
import fr.luteal.core.network.crypto.DuoProjectionDecodeResult
import fr.luteal.core.network.crypto.DuoProjectionDecoder
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Base64
import androidx.annotation.StringRes
import fr.luteal.app.R
import fr.luteal.core.network.contract.models.DuoLink
import fr.luteal.core.network.contract.models.DuoView
import fr.luteal.core.network.contract.models.GrantField
import fr.luteal.core.network.contract.models.Invitation
import fr.luteal.core.network.contract.models.SupportKind
import fr.luteal.core.network.contract.models.SupportRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DuoViewModel @Inject constructor(
    private val duoRepository: DuoRepository,
    private val duoKeyStore: DuoKeyStore,
    private val cycleRepository: CycleRepository,
    private val dailyEntryRepository: DailyEntryRepository,
    private val userRepository: UserRepository,
    private val projectionDecoder: DuoProjectionDecoder,
    private val widgetCacheWriter: DuoCycleProjectionCacheWriter,
    private val widgetCacheRepository: DuoWidgetCacheRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuoUiState())

    // True once the server (or a successful grant toggle) confirmed the
    // grants in this session. publishProjection() refuses to run before
    // that: sealing with an unknown grant set would publish "nothing
    // shared" and silently wipe the partner's view on a cold start.
    private var grantsConfirmed = false
    val uiState: StateFlow<DuoUiState> = _uiState.asStateFlow()

    // Deliberately no init { refresh() }. The tab scaffold is a `when`, not a
    // NavHost, so hiltViewModel() scopes this to the Activity and it outlives
    // every tab switch. A one-shot load at construction would latch whatever
    // was true on the first visit - most damagingly NoAccount, shown forever
    // after the user registers from Settings. DuoScreen refreshes on entry
    // instead; see the LaunchedEffect there.

    fun refresh() {
        viewModelScope.launch {
            val cached = widgetCacheRepository.getLatest()
            if (!duoRepository.hasAccount() && cached == null) {
                _uiState.update { it.copy(phase = DuoPhase.NoAccount, isLoading = false) }
                return@launch
            }

            // Show cached projection immediately so offline and demo data render instantly
            applyCachedProjection()
            seedGrantsFromCache()

            if (!duoRepository.hasAccount()) {
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { duoRepository.duoView() }
                .onSuccess { view ->
                    val isTracker =
                        view.role == fr.luteal.core.network.contract.models.DuoRole.TRACKER
                    applyDuoView(view)
                    // The tracker owns the projection: refresh republishes it so
                    // the partner sees current data and revoked grants drop out.
                    // applyDuoView only confirms the grants when the server
                    // actually returned them, so an unknown grant set never
                    // triggers an empty republish.
                    if (isTracker) publishProjection()
                }
                .onFailure { err ->
                    val missingLink = err is fr.luteal.core.network.FolicularApiException && err.status == 404
                    val cached = widgetCacheRepository.getLatest()
                    if (missingLink && cached == null) {
                        _uiState.update { it.copy(isLoading = false) }
                        discoverLinks()
                    } else {
                        applyCachedProjection()
                        _uiState.update { it.copy(isLoading = false, error = if (cached != null) null else err.message) }
                    }
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
                            .onSuccess { view -> applyDuoView(view) }
                            .onFailure {
                                seedGrantsFromCache()
                                _uiState.update {
                                    it.copy(
                                        phase = if (active.role == fr.luteal.core.network.contract.models.DuoRole.TRACKER)
                                            DuoPhase.TrackerActive else DuoPhase.PartnerActive,
                                        activeLinkId = active.id.toString()
                                    )
                                }
                            }
                    } else if (pending != null) {
                        widgetCacheRepository.clear()
                        _uiState.update {
                            it.copy(
                                phase = DuoPhase.InvitationPending,
                                activeLinkId = pending.id.toString()
                            )
                        }
                    } else {
                        widgetCacheRepository.clear()
                        _uiState.update { it.copy(phase = DuoPhase.NoLink) }
                    }
                }
                .onFailure {
                    val cached = widgetCacheRepository.getLatest()
                    if (cached != null) {
                        applyCachedProjection()
                    } else {
                        _uiState.update { it.copy(phase = DuoPhase.NoLink) }
                    }
                }
        }
    }

    /**
     * Applies a fresh [DuoView] to the UI state. The server's grant list is
     * authoritative when present; the locally cached map (last confirmed
     * choices) is kept as the fallback when the server does not expose the
     * field, and the cache is updated from the server otherwise.
     */
    private suspend fun applyDuoView(view: DuoView) {
        val isTracker =
            view.role == fr.luteal.core.network.contract.models.DuoRole.TRACKER
        val serverGrants = view.grants
        if (serverGrants != null) {
            grantsConfirmed = true
            persistGrants(serverGrants)
        }
        val decodedProjection = projectionDecoder.decode(view)
        widgetCacheWriter.save(view)
        val projection = (decodedProjection as? DuoProjectionDecodeResult.Available)?.projection
        val refreshedAt = widgetCacheRepository.getLatest()?.refreshedAt ?: Instant.now()
        val partnerPhase = PartnerPhaseResolver.resolve(projection, LocalDate.now())
        val partnerTip = (partnerPhase as? CurrentCyclePhase.Available)?.let { available ->
            PartnerPhaseTips.forDate(available.phase, LocalDate.now())
        }
        _uiState.update {
            it.copy(
                phase = if (isTracker) DuoPhase.TrackerActive else DuoPhase.PartnerActive,
                duoView = view,
                activeLinkId = view.linkId.toString(),
                projection = projection,
                supportMessages = openSupportMessages(view),
                grants = serverGrants?.associateWith { true } ?: it.grants,
                keyMissing = decodedProjection == DuoProjectionDecodeResult.KeyMissing,
                isLoading = false,
                partnerPhase = partnerPhase,
                partnerTip = partnerTip,
                lastRefreshedAt = refreshedAt,
                freshness = WidgetFreshness.of(refreshedAt)
            )
        }
    }

    /** Seeds the toggle state from the local cache (last confirmed choices). */
    private suspend fun seedGrantsFromCache() {
        val prefs = userRepository.getUserPreferences().first().duoSharing
        _uiState.update { it.copy(grants = prefs.toGrantMap()) }
    }

    /** Mirrors the server's grant list into the local cache. */
    private suspend fun persistGrants(serverGrants: List<GrantField>) {
        DuoSharingField.entries.forEach { field ->
            userRepository.updateDuoSharing(field, serverGrants.contains(field.toGrantField()))
        }
    }

    private fun DuoSharingPreferences.toGrantMap(): Map<GrantField, Boolean> = mapOf(
        GrantField.CYCLE_DAY to shareCycleDay,
        GrantField.PERIOD_ESTIMATE to sharePeriodEstimate,
        GrantField.MOOD to shareMood,
        GrantField.ENERGY to shareEnergy,
        GrantField.SUPPORT_REQUESTS to shareSupportRequests
    )

    private fun DuoSharingField.toGrantField(): GrantField = when (this) {
        DuoSharingField.CYCLE_DAY -> GrantField.CYCLE_DAY
        DuoSharingField.PERIOD_ESTIMATE -> GrantField.PERIOD_ESTIMATE
        DuoSharingField.MOOD -> GrantField.MOOD
        DuoSharingField.ENERGY -> GrantField.ENERGY
        DuoSharingField.SUPPORT_REQUESTS -> GrantField.SUPPORT_REQUESTS
    }

    private fun GrantField.toDuoSharingField(): DuoSharingField = when (this) {
        GrantField.CYCLE_DAY -> DuoSharingField.CYCLE_DAY
        GrantField.PERIOD_ESTIMATE -> DuoSharingField.PERIOD_ESTIMATE
        GrantField.MOOD -> DuoSharingField.MOOD
        GrantField.ENERGY -> DuoSharingField.ENERGY
        GrantField.SUPPORT_REQUESTS -> DuoSharingField.SUPPORT_REQUESTS
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
            _uiState.update { it.copy(error = null, errorResId = R.string.duo_error_invalid_pairing_link, isLoading = false) }
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
                    // projection must reflect the new grant immediately (a
                    // republish against the stale map would keep revoked
                    // fields flowing and delay newly granted ones).
                    _uiState.update { state ->
                        state.copy(grants = state.grants + (field to granted))
                    }
                    // Keep the local cache in step so a later cold start shows
                    // the last confirmed choices even before the server answers.
                    userRepository.updateDuoSharing(field.toDuoSharingField(), granted)
                    publishProjection()
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
                    widgetCacheRepository.clear()
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
            _uiState.update { it.copy(error = null, errorResId = R.string.duo_error_invalid_pairing_link) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingSupport = true, error = null) }
            // Sealed before it leaves the device: the server relays support
            // messages but never reads them. The wire carries base64 (Go
            // decodes []byte from a base64 string, not a JSON number array).
            val sealed = Base64.getEncoder().encodeToString(
                DuoCrypto.sealRaw(key, linkId, message.toByteArray())
            )
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

    fun applyQuickNudge(text: String, kind: SupportKind, sendImmediately: Boolean) {
        if (sendImmediately) {
            sendSupportRequest(kind, text)
        } else {
            onSupportDraftChange(text)
        }
    }

    private suspend fun applyCachedProjection() {
        val cached = widgetCacheRepository.getLatest() ?: return
        val projection = DuoProjection(
            cycleDay = cached.cycleDay,
            periodEstimate = if (cached.estimateStart != null && cached.estimateEnd != null) {
                SharedEstimate(cached.estimateStart.toString(), cached.estimateEnd.toString())
            } else {
                null
            }
        )
        val partnerPhase = PartnerPhaseResolver.resolve(projection, LocalDate.now())
        val partnerTip = (partnerPhase as? CurrentCyclePhase.Available)?.let { available ->
            PartnerPhaseTips.forDate(available.phase, LocalDate.now())
        }
        _uiState.update {
            it.copy(
                phase = when {
                    cached.role.equals("PARTNER", ignoreCase = true) -> DuoPhase.PartnerActive
                    cached.role.equals("TRACKER", ignoreCase = true) -> DuoPhase.TrackerActive
                    else -> it.phase
                },
                projection = it.projection ?: projection,
                lastRefreshedAt = cached.refreshedAt,
                freshness = WidgetFreshness.of(cached.refreshedAt),
                partnerPhase = partnerPhase,
                partnerTip = partnerTip,
                activeLinkId = it.activeLinkId ?: cached.linkId
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorResId = null) }
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
        // Never seal a projection while the grants are unknown: on a cold
        // start that would publish "nothing shared" and silently wipe the
        // partner's view. The grants are confirmed by the server's duoView
        // (even an empty list) or by a successful toggle in this session.
        if (!grantsConfirmed) return
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
                    // Must use the same inputs as the tracker's own estimate.
                    // Called without them, this recomputed the window from the
                    // undeclared prior, so a partner saw a narrower and more
                    // confident range than the tracker did for the same cycles.
                    val preferences = userRepository.getUserPreferences().first()
                    CycleEstimateCalculator.estimateNextPeriod(
                        cycles = cycles,
                        ageBand = AgeBand.fromId(preferences.ageBand),
                        hasTimingContext = preferences.hasTimingContext
                    )?.let {
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
                widgetCacheWriter.savePublished(linkId, projection, grants)
                _uiState.update { it.copy(projection = projection) }
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message) }
            }
        }
    }
}

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
    @param:StringRes val errorResId: Int? = null,
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
    val isSendingSupport: Boolean = false,
    val partnerPhase: CurrentCyclePhase = CurrentCyclePhase.Indeterminate(
        PhaseIndeterminateReason.NO_CURRENT_CYCLE
    ),
    val partnerTip: PartnerPhaseTip? = null,
    val lastRefreshedAt: Instant? = null,
    val freshness: WidgetFreshness = WidgetFreshness.CURRENT
)