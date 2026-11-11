package com.thinkofme.core.data.demo

import com.thinkofme.core.data.AuthRepository
import com.thinkofme.core.data.CircleRepository
import com.thinkofme.core.data.ClaimRepository
import com.thinkofme.core.data.Invite
import com.thinkofme.core.data.InvitePreview
import com.thinkofme.core.data.LinkPreview
import com.thinkofme.core.data.OccasionRepository
import com.thinkofme.core.data.ProfileRepository
import com.thinkofme.core.data.WishRepository
import com.thinkofme.core.model.AboutMe
import com.thinkofme.core.model.Circle
import com.thinkofme.core.model.CircleKind
import com.thinkofme.core.model.Claim
import com.thinkofme.core.model.ClaimStatus
import com.thinkofme.core.model.Money
import com.thinkofme.core.model.Occasion
import com.thinkofme.core.model.OccasionKind
import com.thinkofme.core.model.Person
import com.thinkofme.core.model.Priority
import com.thinkofme.core.model.SessionUser
import com.thinkofme.core.model.Wish
import com.thinkofme.core.model.WishDraft
import com.thinkofme.core.model.WishKind
import com.thinkofme.core.model.WishStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory sample world used when the app runs without Firebase. It mirrors
 * the backend's visibility rules, including the surprise guarantee: claims on
 * your own wishes are never returned.
 */
@Singleton
class DemoStore @Inject constructor() {
    val me = "me"
    private val today = LocalDate.now()
    private val now = System.currentTimeMillis()
    private fun daysAgo(days: Int) = now - days * 86_400_000L

    val session = MutableStateFlow<SessionUser?>(null)

    val people = MutableStateFlow(
        mapOf(
            me to Person(me, "Dana", hueIndex = 2, aboutMe = AboutMe(clothingSize = "M", shoeSize = "39", favoriteColors = "Forest green, cream")),
            "maya" to Person("maya", "Maya", hueIndex = 0, aboutMe = AboutMe(clothingSize = "S", shoeSize = "38", ringSize = "6", favoriteColors = "Black, terracotta", dontBuy = "Candles, please, I have enough")),
            "mom" to Person("mom", "Mom", hueIndex = 1, aboutMe = AboutMe(allergies = "Lilies")),
            "dan" to Person("dan", "Dan", hueIndex = 2),
            "noa" to Person("noa", "Noa", hueIndex = 3),
        ),
    )

    val circles = MutableStateFlow(
        listOf(
            Circle("us", "Maya & me", CircleKind.PAIR, me, listOf(me, "maya"), listOf(me)),
            Circle("family", "Family", CircleKind.GROUP, "mom", listOf(me, "maya", "mom", "dan", "noa"), listOf("mom", me)),
        ),
    )

    private fun on(daysFromNow: Long) = today.plusDays(daysFromNow)

    val occasions = MutableStateFlow(
        listOf(
            on(12).let { Occasion("o1", "maya", "maya", OccasionKind.BIRTHDAY, "Birthday", it.monthValue, it.dayOfMonth, it.year - 34, true, listOf("us", "family")) },
            on(21).let { Occasion("o2", "mom", "mom", OccasionKind.ANNIVERSARY, "Mom & Dad's anniversary", it.monthValue, it.dayOfMonth, it.year - 38, true, listOf("family")) },
            on(37).let { Occasion("o3", "dan", "dan", OccasionKind.BIRTHDAY, "Birthday", it.monthValue, it.dayOfMonth, null, true, listOf("family")) },
            on(95).let { Occasion("o4", "noa", "noa", OccasionKind.BIRTHDAY, "Birthday", it.monthValue, it.dayOfMonth, null, true, listOf("family")) },
            on(64).let { Occasion("o5", me, me, OccasionKind.BIRTHDAY, "Birthday", it.monthValue, it.dayOfMonth, it.year - 33, true, listOf("us", "family")) },
        ),
    )

    val wishes = MutableStateFlow(
        listOf(
            Wish("w1", "maya", "Noise-cancelling headphones", "Matte black please, for flights and deep-work days.", listOf("https://example.com/headphones"), price = Money(349.0, "USD"), store = "Soundhouse", priority = Priority.LOVE, visibleCircleIds = listOf("us", "family"), createdAtMillis = daysAgo(2)),
            Wish("w2", "maya", "Ceramics class for two", "Any weekend works. Let's go together!", kind = WishKind.IDEA, priority = Priority.DREAM, visibleCircleIds = listOf("us"), createdAtMillis = daysAgo(1)),
            Wish("w3", "maya", "Italian cookbook", price = Money(32.0, "USD"), store = "Bookshop", priority = Priority.NICE, visibleCircleIds = listOf("us", "family"), createdAtMillis = daysAgo(30)),
            Wish("w4", "maya", "Silk scarf, terracotta", price = Money(85.0, "USD"), store = "Atelier", visibleCircleIds = listOf("family", "us"), createdAtMillis = daysAgo(40)),
            Wish("w5", "maya", "Running socks (3 pairs)", price = Money(18.0, "USD"), quantity = 3, priority = Priority.NICE, visibleCircleIds = listOf("family"), createdAtMillis = daysAgo(50)),
            Wish("w6", "mom", "Large monstera plant", "In a ceramic pot if possible.", price = Money(65.0, "USD"), store = "Green House", visibleCircleIds = listOf("family"), createdAtMillis = daysAgo(20)),
            Wish("w7", "dan", "Instant film camera", price = Money(129.0, "USD"), store = "PhotoLab", priority = Priority.DREAM, visibleCircleIds = listOf("family"), createdAtMillis = daysAgo(3)),
            Wish("w8", "noa", "Climbing gym pass", kind = WishKind.IDEA, visibleCircleIds = listOf("family"), createdAtMillis = daysAgo(60)),
            Wish("w9", me, "Linen bedding set", "Sage green, queen size.", price = Money(180.0, "USD"), store = "Linen Co.", priority = Priority.LOVE, visibleCircleIds = listOf("us", "family"), createdAtMillis = daysAgo(5)),
            Wish("w10", me, "Pour-over coffee kit", price = Money(45.0, "USD"), priority = Priority.NICE, visibleCircleIds = listOf("us", "family"), createdAtMillis = daysAgo(9)),
            Wish("w11", me, "Weekend in the mountains", "Anywhere with a fireplace.", kind = WishKind.IDEA, priority = Priority.DREAM, visibleCircleIds = listOf("us"), createdAtMillis = daysAgo(14)),
        ),
    )

    // Noa is getting Maya's headphones. Someone is also getting Dana's coffee
    // kit, which Dana must never see.
    val claims = MutableStateFlow(
        listOf(
            Claim("w1", "maya", "noa", "Noa", ClaimStatus.RESERVED),
            Claim("w10", me, "maya", "Maya", ClaimStatus.PURCHASED),
        ),
    )

    fun viewersOf(ownerId: String, circleIds: List<String>): Set<String> =
        circles.value.filter { it.id in circleIds && ownerId in it.memberIds }.flatMap { it.memberIds }.toSet() - ownerId
}

class DemoAuthRepository @Inject constructor(private val store: DemoStore) : AuthRepository {
    override val session: StateFlow<SessionUser?> = store.session.asStateFlow()
    override val isDemo = true

    override suspend fun signInWithGoogleIdToken(idToken: String) = signInWithDemoData()

    override suspend fun signInWithDemoData() {
        delay(250)
        store.session.value = SessionUser(store.me, "Dana", null, null)
    }

    override suspend fun signOut() {
        store.session.value = null
    }

    override suspend fun deleteAccount() = signOut()
}

class DemoProfileRepository @Inject constructor(private val store: DemoStore) : ProfileRepository {
    override fun me(): Flow<Person?> = store.people.map { it[store.me] }
    override fun people(): Flow<Map<String, Person>> = store.people

    override suspend fun saveProfile(displayName: String, aboutMe: AboutMe) {
        store.people.update { it + (store.me to it.getValue(store.me).copy(displayName = displayName, aboutMe = aboutMe)) }
    }

    override suspend fun saveDeviceSettings(timeZone: String, locale: String, pushToken: String?) = Unit
}

class DemoCircleRepository @Inject constructor(private val store: DemoStore) : CircleRepository {
    override fun circles(): Flow<List<Circle>> = store.circles.map { list -> list.filter { store.me in it.memberIds } }

    override suspend fun create(name: String, kind: CircleKind): String {
        val id = UUID.randomUUID().toString()
        store.circles.update { it + Circle(id, name, kind, store.me, listOf(store.me), listOf(store.me)) }
        return id
    }

    override suspend fun rename(circleId: String, name: String) {
        store.circles.update { list -> list.map { if (it.id == circleId) it.copy(name = name) else it } }
    }

    override suspend fun createInvite(circleId: String): Invite {
        delay(300)
        return Invite("H7K2PQ9M", "https://thinkofme.app/i/H7K2PQ9M", System.currentTimeMillis() + 7 * 86_400_000L)
    }

    override suspend fun previewInvite(code: String) = InvitePreview("Friends", CircleKind.GROUP, "Noa", 4)

    override suspend fun joinWithInvite(code: String): String = create("Friends", CircleKind.GROUP)

    override suspend fun leave(circleId: String) {
        store.circles.update { list -> list.filterNot { it.id == circleId } }
    }
}

class DemoWishRepository @Inject constructor(private val store: DemoStore) : WishRepository {
    override fun myWishes(): Flow<List<Wish>> = store.wishes.map { list -> list.filter { it.ownerId == store.me }.sortedByDescending { it.createdAtMillis } }

    override fun visibleWishes(): Flow<List<Wish>> = combine(store.wishes, store.circles) { wishes, _ ->
        wishes.filter { it.ownerId != store.me && store.me in store.viewersOf(it.ownerId, it.visibleCircleIds) }
    }

    override fun wishesOf(personId: String): Flow<List<Wish>> =
        if (personId == store.me) myWishes() else visibleWishes().map { list -> list.filter { it.ownerId == personId }.sortedByDescending { it.createdAtMillis } }

    override fun wish(wishId: String): Flow<Wish?> = store.wishes.map { list -> list.firstOrNull { it.id == wishId } }

    override suspend fun save(wishId: String?, draft: WishDraft): String {
        val id = wishId ?: UUID.randomUUID().toString()
        val existing = store.wishes.value.firstOrNull { it.id == id }
        val wish = Wish(
            id = id,
            ownerId = store.me,
            title = draft.title.trim(),
            note = draft.note.trim().ifEmpty { null },
            urls = listOfNotNull(draft.url.trim().ifEmpty { null }),
            imageUrls = draft.imageUrls,
            price = draft.priceText.replace(',', '.').toDoubleOrNull()?.let { Money(it, draft.currency) },
            store = draft.store.trim().ifEmpty { null },
            kind = draft.kind,
            priority = draft.priority,
            status = existing?.status ?: WishStatus.ACTIVE,
            visibleCircleIds = draft.visibleCircleIds,
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
        )
        store.wishes.update { list -> list.filterNot { it.id == id } + wish }
        return id
    }

    override suspend fun markReceived(wishId: String) {
        store.wishes.update { list -> list.map { if (it.id == wishId) it.copy(status = WishStatus.RECEIVED) else it } }
    }

    override suspend fun delete(wishId: String) {
        store.wishes.update { list -> list.filterNot { it.id == wishId } }
        store.claims.update { list -> list.filterNot { it.wishId == wishId } }
    }

    override suspend fun unfurl(url: String): LinkPreview {
        delay(700)
        val host = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull()
        return LinkPreview(url, null, emptyList(), host, null, null)
    }
}

class DemoClaimRepository @Inject constructor(private val store: DemoStore) : ClaimRepository {
    override fun claimsOnWishesOf(personId: String): Flow<Map<String, List<Claim>>> = store.claims.map { claims ->
        // The surprise guarantee: never reveal claims on your own wishes.
        if (personId == store.me) emptyMap() else claims.filter { it.wishOwnerId == personId }.groupBy { it.wishId }
    }

    override fun myClaims(): Flow<List<Claim>> = store.claims.map { list -> list.filter { it.claimerId == store.me } }

    override suspend fun claim(wish: Wish) {
        require(wish.ownerId != store.me) { "You can't claim your own wish." }
        store.claims.update { list ->
            list.filterNot { it.wishId == wish.id && it.claimerId == store.me } +
                Claim(wish.id, wish.ownerId, store.me, store.people.value[store.me]?.displayName ?: "", ClaimStatus.RESERVED)
        }
    }

    override suspend fun release(wishId: String) {
        store.claims.update { list -> list.filterNot { it.wishId == wishId && it.claimerId == store.me } }
    }
}

class DemoOccasionRepository @Inject constructor(private val store: DemoStore) : OccasionRepository {
    override fun occasions(): Flow<List<Occasion>> = combine(store.occasions, store.circles) { occasions, _ ->
        occasions.filter { it.ownerId == store.me || store.me in store.viewersOf(it.ownerId, it.visibleCircleIds) }
    }

    override suspend fun save(occasion: Occasion): String {
        val id = occasion.id.ifEmpty { UUID.randomUUID().toString() }
        store.occasions.update { list -> list.filterNot { it.id == id } + occasion.copy(id = id, ownerId = store.me) }
        return id
    }

    override suspend fun delete(occasionId: String) {
        store.occasions.update { list -> list.filterNot { it.id == occasionId } }
    }
}
