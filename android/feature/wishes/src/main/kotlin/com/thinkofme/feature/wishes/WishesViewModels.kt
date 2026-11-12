package com.thinkofme.feature.wishes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.thinkofme.core.data.AuthRepository
import com.thinkofme.core.data.CircleRepository
import com.thinkofme.core.data.ClaimRepository
import com.thinkofme.core.data.ObserveOverview
import com.thinkofme.core.data.OccasionRepository
import com.thinkofme.core.data.ProfileRepository
import com.thinkofme.core.data.Today
import com.thinkofme.core.data.WishRepository
import com.thinkofme.core.model.ALL_CIRCLES
import com.thinkofme.core.model.Circle
import com.thinkofme.core.model.Claim
import com.thinkofme.core.model.Person
import com.thinkofme.core.model.UpcomingOccasion
import com.thinkofme.core.model.Wish
import com.thinkofme.core.model.WishDraft
import com.thinkofme.core.model.WishStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------- person ----------

data class PersonUiState(
    val loading: Boolean = true,
    val person: Person? = null,
    val isMe: Boolean = false,
    val wishes: List<Wish> = emptyList(),
    /** Wish ids someone is getting. Always empty when viewing your own list. */
    val wrappedIds: Set<String> = emptySet(),
    val nextOccasion: UpcomingOccasion? = null,
)

@HiltViewModel
class PersonViewModel @Inject constructor(
    savedState: SavedStateHandle,
    profiles: ProfileRepository,
    wishes: WishRepository,
    claims: ClaimRepository,
    occasions: OccasionRepository,
    auth: AuthRepository,
    today: Today,
) : ViewModel() {
    private val personId = savedState.toRoute<PersonRoute>().personId

    val state: StateFlow<PersonUiState> = combine(
        profiles.people(),
        wishes.wishesOf(personId),
        claims.claimsOnWishesOf(personId),
        occasions.occasions(),
        auth.session,
    ) { people, list, claimMap, occasionList, session ->
        val isMe = session?.id == personId
        PersonUiState(
            loading = false,
            person = people[personId],
            isMe = isMe,
            wishes = list.filter { it.status == WishStatus.ACTIVE },
            wrappedIds = if (isMe) emptySet() else claimMap.keys,
            nextOccasion = ObserveOverview.upcoming(occasionList.filter { (it.personId ?: it.ownerId) == personId }, people, today()).firstOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUiState())
}

// ---------- wish detail ----------

data class WishUiState(
    val loading: Boolean = true,
    val wish: Wish? = null,
    val owner: Person? = null,
    val isMine: Boolean = false,
    val claims: List<Claim> = emptyList(),
    val myClaim: Claim? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class WishViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val wishes: WishRepository,
    private val claims: ClaimRepository,
    profiles: ProfileRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val wishId = savedState.toRoute<WishRoute>().wishId
    private val ownerId = savedState.toRoute<WishRoute>().ownerId
    private val transient = MutableStateFlow(false to false) // busy, error

    val state: StateFlow<WishUiState> = combine(
        wishes.wish(wishId),
        profiles.people(),
        claims.claimsOnWishesOf(ownerId),
        auth.session,
        transient,
    ) { wish, people, claimMap, session, (busy, error) ->
        val isMine = wish?.ownerId == session?.id
        // Defense in depth: never surface claims to the wish's owner.
        val visibleClaims = if (isMine) emptyList() else claimMap[wishId].orEmpty()
        WishUiState(
            loading = false,
            wish = wish,
            owner = wish?.let { people[it.ownerId] },
            isMine = isMine,
            claims = visibleClaims,
            myClaim = visibleClaims.firstOrNull { it.claimerId == session?.id },
            busy = busy,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WishUiState())

    fun toggleClaim() = run {
        val current = state.value
        val wish = current.wish ?: return@run
        if (current.myClaim == null) claims.claim(wish) else claims.release(wish.id)
    }

    fun markReceived() = run { wishes.markReceived(wishId) }

    fun delete(onDeleted: () -> Unit) = run {
        wishes.delete(wishId)
        onDeleted()
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            transient.value = true to false
            val ok = runCatching { block() }.isSuccess
            transient.value = false to !ok
        }
    }
}

// ---------- my list ----------

data class MyListUiState(val loading: Boolean = true, val wishes: List<Wish> = emptyList(), val me: Person? = null)

@HiltViewModel
class MyListViewModel @Inject constructor(wishes: WishRepository, profiles: ProfileRepository) : ViewModel() {
    val state: StateFlow<MyListUiState> = combine(wishes.myWishes(), profiles.me()) { list, me ->
        MyListUiState(false, list.filter { it.status == WishStatus.ACTIVE }, me)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListUiState())
}

// ---------- edit wish ----------

data class EditWishUiState(
    val draft: WishDraft = WishDraft(),
    val circles: List<Circle> = emptyList(),
    val isNew: Boolean = true,
    val unfurling: Boolean = false,
    val unfurlFailed: Boolean = false,
    val uploadingPhoto: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class EditWishViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val wishes: WishRepository,
    circles: CircleRepository,
) : ViewModel() {
    private val route = savedState.toRoute<EditWishRoute>()

    /** Stable id for this wish, also for a new one, so photos can be stored under it. */
    private val wishId: String = route.wishId ?: savedState.get<String>("draftWishId") ?: wishes.newWishId().also { savedState["draftWishId"] = it }
    // New wishes are shared with all of your circles (including future ones) by default.
    private val _state = MutableStateFlow(EditWishUiState(isNew = route.wishId == null, draft = WishDraft(visibleCircleIds = listOf(ALL_CIRCLES))))
    val state: StateFlow<EditWishUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            circles.circles().collect { list ->
                _state.update { it.copy(circles = list) }
            }
        }
        route.wishId?.let { id ->
            viewModelScope.launch {
                val wish = wishes.wish(id).first() ?: return@launch
                _state.update {
                    it.copy(
                        draft = WishDraft(
                            title = wish.title, note = wish.note.orEmpty(), url = wish.urls.firstOrNull().orEmpty(),
                            imageUrls = wish.imageUrls, priceText = wish.price?.amount?.let(::formatAmount).orEmpty(),
                            currency = wish.price?.currency ?: "USD", store = wish.store.orEmpty(), kind = wish.kind,
                            priority = wish.priority, visibleCircleIds = wish.visibleCircleIds,
                        ),
                    )
                }
            }
        }
        route.sharedText?.let { text -> extractUrl(text)?.let { url -> update { copy(url = url) }; unfurl() } }
    }

    fun update(change: WishDraft.() -> WishDraft) = _state.update { it.copy(draft = it.draft.change(), error = false) }

    /** "All my circles" and specific circles are mutually exclusive choices. */
    fun toggleCircle(id: String) = update {
        copy(visibleCircleIds = toggleVisibility(visibleCircleIds, id))
    }

    /** Fetch title, photo and price for the pasted link. Fields the user already typed win. */
    fun unfurl() {
        val url = _state.value.draft.url.trim()
        if (!url.startsWith("http")) return
        viewModelScope.launch {
            _state.update { it.copy(unfurling = true, unfurlFailed = false) }
            val result = runCatching { wishes.unfurl(url) }.getOrNull()
            _state.update { s ->
                if (result == null) {
                    s.copy(unfurling = false, unfurlFailed = true)
                } else {
                    s.copy(
                        unfurling = false,
                        unfurlFailed = result.title == null && result.imageUrls.isEmpty(),
                        draft = s.draft.copy(
                            title = s.draft.title.ifBlank { result.title.orEmpty() },
                            imageUrls = s.draft.imageUrls.ifEmpty { result.imageUrls.take(4) },
                            priceText = s.draft.priceText.ifBlank { result.priceAmount?.let(::formatAmount).orEmpty() },
                            currency = result.priceCurrency ?: s.draft.currency,
                            store = s.draft.store.ifBlank { result.siteName.orEmpty() },
                        ),
                    )
                }
            }
        }
    }

    /** Adds a photo (already downscaled to a JPEG) to the wish. */
    fun addPhoto(jpeg: ByteArray) {
        if (_state.value.draft.imageUrls.size >= 8) return
        viewModelScope.launch {
            _state.update { it.copy(uploadingPhoto = true, error = false) }
            val url = runCatching { wishes.uploadPhoto(wishId, jpeg) }.getOrNull()
            _state.update { s ->
                if (url == null) s.copy(uploadingPhoto = false, error = true)
                else s.copy(uploadingPhoto = false, draft = s.draft.copy(imageUrls = listOf(url) + s.draft.imageUrls))
            }
        }
    }

    fun removePhoto(url: String) = update { copy(imageUrls = imageUrls - url) }

    fun save() {
        val s = _state.value
        if (!s.draft.isValid || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = false) }
            val ok = runCatching { wishes.save(wishId, isNew = route.wishId == null, draft = s.draft) }.isSuccess
            _state.update { it.copy(saving = false, saved = ok, error = !ok) }
        }
    }

    companion object {
        fun toggleVisibility(current: List<String>, id: String): List<String> = when {
            id == ALL_CIRCLES -> if (ALL_CIRCLES in current) emptyList() else listOf(ALL_CIRCLES)
            id in current -> current - id
            else -> current - ALL_CIRCLES + id
        }

        private val urlRegex = Regex("""https?://\S+""")
        fun extractUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', '"')
        fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, value)
    }
}
