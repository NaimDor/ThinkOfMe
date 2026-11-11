package com.thinkofme.core.data.firebase

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val REGION = "europe-west1"

internal fun firestore(): FirebaseFirestore = Firebase.firestore
internal fun functions(): FirebaseFunctions = Firebase.functions(REGION)

/** Emits the current uid (or null) and follows sign-in / sign-out. */
@Singleton
class FirebaseSession @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob())
    private val auth: FirebaseAuth get() = Firebase.auth

    val user: StateFlow<SessionUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.let { SessionUser(it.uid, it.displayName.orEmpty(), it.email, it.photoUrl?.toString()) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, auth.currentUser?.let { SessionUser(it.uid, it.displayName.orEmpty(), it.email, it.photoUrl?.toString()) })

    val uid: String? get() = auth.currentUser?.uid

    fun requireUid(): String = uid ?: error("Not signed in")

    /** Re-runs [block] for each signed-in user; emits [empty] while signed out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> whenSignedIn(empty: T, block: (String) -> Flow<T>): Flow<T> =
        user.map { it?.id }.flatMapLatest { uid -> if (uid == null) flowOf(empty) else block(uid) }
}

// ---------- mapping ----------

private fun DocumentSnapshot.strings(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

internal fun DocumentSnapshot.toPerson(): Person {
    @Suppress("UNCHECKED_CAST")
    val about = get("aboutMe") as? Map<String, Any?> ?: emptyMap()
    return Person(
        id = id,
        displayName = getString("displayName").orEmpty(),
        photoUrl = getString("photoUrl"),
        hueIndex = getLong("hue")?.toInt() ?: Math.floorMod(id.hashCode(), 8),
        aboutMe = AboutMe(
            clothingSize = about["clothingSize"] as? String,
            shoeSize = about["shoeSize"] as? String,
            ringSize = about["ringSize"] as? String,
            favoriteColors = about["favoriteColors"] as? String,
            allergies = about["allergies"] as? String,
            dontBuy = about["dontBuy"] as? String,
        ),
    )
}

internal fun DocumentSnapshot.toCircle() = Circle(
    id = id,
    name = getString("name").orEmpty(),
    kind = if (getString("kind") == "pair") CircleKind.PAIR else CircleKind.GROUP,
    ownerId = getString("ownerId").orEmpty(),
    memberIds = strings("memberIds"),
    adminIds = strings("adminIds"),
)

internal fun DocumentSnapshot.toWish(): Wish {
    @Suppress("UNCHECKED_CAST")
    val price = get("price") as? Map<String, Any?>
    return Wish(
        id = id,
        ownerId = getString("ownerId").orEmpty(),
        title = getString("title").orEmpty(),
        note = getString("note"),
        urls = strings("urls"),
        imageUrls = strings("imageUrls"),
        price = price?.let { p ->
            val amount = (p["amount"] as? Number)?.toDouble()
            val currency = p["currency"] as? String
            if (amount != null && currency != null) Money(amount, currency) else null
        },
        store = getString("store"),
        kind = if (getString("kind") == "idea") WishKind.IDEA else WishKind.PRODUCT,
        priority = Priority.of(getLong("priority")?.toInt() ?: 2),
        quantity = getLong("quantity")?.toInt() ?: 1,
        status = when (getString("status")) {
            "received" -> WishStatus.RECEIVED
            "archived" -> WishStatus.ARCHIVED
            else -> WishStatus.ACTIVE
        },
        visibleCircleIds = strings("visibleCircleIds"),
        createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
    )
}

internal fun DocumentSnapshot.toClaim() = Claim(
    wishId = getString("wishId").orEmpty(),
    wishOwnerId = getString("wishOwnerId").orEmpty(),
    claimerId = getString("claimerId").orEmpty(),
    claimerName = getString("claimerName").orEmpty(),
    status = if (getString("status") == "purchased") ClaimStatus.PURCHASED else ClaimStatus.RESERVED,
    quantity = getLong("quantity")?.toInt() ?: 1,
)

internal fun DocumentSnapshot.toOccasion() = Occasion(
    id = id,
    ownerId = getString("ownerId").orEmpty(),
    personId = getString("personId"),
    kind = when (getString("kind")) {
        "birthday" -> OccasionKind.BIRTHDAY
        "anniversary" -> OccasionKind.ANNIVERSARY
        "holiday" -> OccasionKind.HOLIDAY
        else -> OccasionKind.CUSTOM
    },
    title = getString("title").orEmpty(),
    month = getLong("month")?.toInt() ?: 1,
    day = getLong("day")?.toInt() ?: 1,
    year = getLong("year")?.toInt(),
    yearly = getString("recurrence") != "once",
    visibleCircleIds = strings("visibleCircleIds"),
)

private fun Query.liveList(): Flow<List<DocumentSnapshot>> = snapshots().map { it.documents }

// ---------- repositories ----------

class FirebaseAuthRepository @Inject constructor(firebaseSession: FirebaseSession) : AuthRepository {
    override val session: StateFlow<SessionUser?> = firebaseSession.user
    override val isDemo = false

    override suspend fun signInWithGoogleIdToken(idToken: String) {
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override suspend fun signInWithDemoData() = error("Demo data is only available in demo builds.")

    override suspend fun signOut() = Firebase.auth.signOut()

    override suspend fun deleteAccount() {
        functions().getHttpsCallable("deleteAccount").call().await()
        Firebase.auth.signOut()
    }
}

class FirebaseProfileRepository @Inject constructor(private val session: FirebaseSession) : ProfileRepository {
    private val users get() = firestore().collection("users")

    override fun me(): Flow<Person?> = session.whenSignedIn(null) { uid ->
        users.document(uid).snapshots().map { if (it.exists()) it.toPerson() else null }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun people(): Flow<Map<String, Person>> = session.whenSignedIn(emptyMap()) { uid ->
        firestore().collection("connections").document(uid).snapshots()
            .map { (it.get("userIds") as? List<*>)?.filterIsInstance<String>().orEmpty() + uid }
            .flatMapLatest { ids ->
                // Firestore `in` queries take up to 30 ids; chunk and merge.
                val chunks = ids.distinct().chunked(30)
                combine(chunks.map { chunk -> users.whereIn(FieldPath.documentId(), chunk).liveList() }) { lists ->
                    lists.flatMap { it.toList() }.associate { it.id to it.toPerson() }
                }
            }
    }

    override suspend fun saveProfile(displayName: String, aboutMe: AboutMe) {
        val uid = session.requireUid()
        val about = mapOf(
            "clothingSize" to aboutMe.clothingSize, "shoeSize" to aboutMe.shoeSize, "ringSize" to aboutMe.ringSize,
            "favoriteColors" to aboutMe.favoriteColors, "allergies" to aboutMe.allergies, "dontBuy" to aboutMe.dontBuy,
        ).filterValues { !it.isNullOrBlank() }
        val ref = users.document(uid)
        val exists = ref.get().await().exists()
        val data = mutableMapOf<String, Any>("displayName" to displayName.trim(), "aboutMe" to about, "updatedAt" to FieldValue.serverTimestamp())
        if (!exists) {
            data["createdAt"] = FieldValue.serverTimestamp()
            data["hue"] = Math.floorMod(uid.hashCode(), 8)
            ref.set(data).await()
        } else {
            ref.update(data).await()
        }
    }

    override fun remindersEnabled(): Flow<Boolean> = session.whenSignedIn(true) { uid ->
        users.document(uid).collection("private").document("settings").snapshots().map { it.getBoolean("notifications.reminders") != false }
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        val uid = session.requireUid()
        users.document(uid).collection("private").document("settings")
            .set(mapOf("notifications" to mapOf("reminders" to enabled)), SetOptions.merge()).await()
    }

    override suspend fun saveDeviceSettings(timeZone: String, locale: String, pushToken: String?) {
        val uid = session.uid ?: return
        users.document(uid).collection("private").document("settings")
            .set(mapOf("timeZone" to timeZone, "locale" to locale), SetOptions.merge()).await()
        if (pushToken != null) {
            users.document(uid).collection("devices").document(pushToken.takeLast(40).replace('/', '_'))
                .set(mapOf("token" to pushToken, "platform" to "android", "locale" to locale, "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }
}

class FirebaseCircleRepository @Inject constructor(private val session: FirebaseSession) : CircleRepository {
    private val circles get() = firestore().collection("circles")

    override fun circles(): Flow<List<Circle>> = session.whenSignedIn(emptyList()) { uid ->
        circles.whereArrayContains("memberIds", uid).liveList().map { docs -> docs.map { it.toCircle() }.sortedBy { it.name } }
    }

    override suspend fun create(name: String, kind: CircleKind): String {
        val uid = session.requireUid()
        val ref = circles.document()
        ref.set(
            mapOf(
                "name" to name.trim(), "kind" to if (kind == CircleKind.PAIR) "pair" else "group", "ownerId" to uid,
                "memberIds" to listOf(uid), "adminIds" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return ref.id
    }

    override suspend fun rename(circleId: String, name: String) {
        circles.document(circleId).update(mapOf("name" to name.trim(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun call(name: String, data: Map<String, Any?>): Map<String, Any?> =
        functions().getHttpsCallable(name).call(data).await().getData() as? Map<String, Any?> ?: emptyMap()

    override suspend fun createInvite(circleId: String): Invite {
        val result = call("createInvite", mapOf("circleId" to circleId))
        return Invite(result["code"] as String, result["url"] as String, (result["expiresAt"] as Number).toLong())
    }

    override suspend fun previewInvite(code: String): InvitePreview {
        val result = call("previewInvite", mapOf("code" to code))
        return InvitePreview(
            circleName = result["circleName"] as? String ?: "",
            kind = if (result["kind"] == "pair") CircleKind.PAIR else CircleKind.GROUP,
            inviterName = result["inviterName"] as? String ?: "",
            memberCount = (result["memberCount"] as? Number)?.toInt() ?: 0,
        )
    }

    override suspend fun joinWithInvite(code: String): String = call("redeemInvite", mapOf("code" to code))["circleId"] as String

    override suspend fun leave(circleId: String) {
        call("leaveCircle", mapOf("circleId" to circleId))
    }
}

class FirebaseWishRepository @Inject constructor(private val session: FirebaseSession) : WishRepository {
    private val wishes get() = firestore().collection("wishes")

    override fun myWishes(): Flow<List<Wish>> = session.whenSignedIn(emptyList()) { uid ->
        wishes.whereEqualTo("ownerId", uid).orderBy("sortKey", Query.Direction.DESCENDING).liveList().map { docs -> docs.map { it.toWish() } }
    }

    override fun visibleWishes(): Flow<List<Wish>> = session.whenSignedIn(emptyList()) { uid ->
        wishes.whereArrayContains("viewerIds", uid).orderBy("updatedAt", Query.Direction.DESCENDING).limit(300)
            .liveList().map { docs -> docs.map { it.toWish() } }
    }

    override fun wishesOf(personId: String): Flow<List<Wish>> = session.whenSignedIn(emptyList()) { uid ->
        if (personId == uid) {
            myWishes()
        } else {
            wishes.whereArrayContains("viewerIds", uid).whereEqualTo("ownerId", personId)
                .orderBy("sortKey", Query.Direction.DESCENDING).liveList().map { docs -> docs.map { it.toWish() } }
        }
    }

    override fun wish(wishId: String): Flow<Wish?> =
        wishes.document(wishId).snapshots().map { if (it.exists()) it.toWish() else null }

    override suspend fun save(wishId: String?, draft: WishDraft): String {
        val uid = session.requireUid()
        val ref = wishId?.let { wishes.document(it) } ?: wishes.document()
        val price = draft.priceText.replace(',', '.').toDoubleOrNull()?.let { mapOf("amount" to it, "currency" to draft.currency) }
        val fields = mutableMapOf<String, Any?>(
            "ownerId" to uid,
            "title" to draft.title.trim(),
            "note" to draft.note.trim().ifEmpty { null },
            "urls" to listOfNotNull(draft.url.trim().ifEmpty { null }),
            "imageUrls" to draft.imageUrls.take(8),
            "price" to price,
            "store" to draft.store.trim().ifEmpty { null },
            "kind" to if (draft.kind == WishKind.IDEA) "idea" else "product",
            "priority" to draft.priority.level,
            "quantity" to 1,
            "status" to "active",
            "visibleCircleIds" to draft.visibleCircleIds,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (wishId == null) {
            fields["viewerIds"] = emptyList<String>()
            fields["sortKey"] = System.currentTimeMillis()
            fields["createdAt"] = FieldValue.serverTimestamp()
            ref.set(fields).await()
        } else {
            fields.remove("status")
            ref.update(fields).await()
        }
        return ref.id
    }

    override suspend fun markReceived(wishId: String) {
        wishes.document(wishId).update(mapOf("status" to "received", "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun delete(wishId: String) {
        wishes.document(wishId).delete().await()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun unfurl(url: String): LinkPreview {
        val data = functions().getHttpsCallable("unfurlLink").call(mapOf("url" to url)).await().getData() as? Map<String, Any?> ?: emptyMap()
        val price = data["price"] as? Map<String, Any?>
        return LinkPreview(
            url = data["url"] as? String ?: url,
            title = data["title"] as? String,
            imageUrls = (data["imageUrls"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            siteName = data["siteName"] as? String,
            priceAmount = (price?.get("amount") as? Number)?.toDouble(),
            priceCurrency = price?.get("currency") as? String,
        )
    }
}

class FirebaseClaimRepository @Inject constructor(private val session: FirebaseSession) : ClaimRepository {
    private val claims get() = firestore().collection("claims")

    override fun claimsOnWishesOf(personId: String): Flow<Map<String, List<Claim>>> = session.whenSignedIn(emptyMap()) { uid ->
        // Never query claims on your own wishes: the rules deny it, and the UI must not show them.
        if (personId == uid) {
            flowOf(emptyMap())
        } else {
            claims.whereArrayContains("viewerIds", uid).whereEqualTo("wishOwnerId", personId).liveList()
                .map { docs -> docs.map { it.toClaim() }.groupBy { it.wishId } }
        }
    }

    override fun myClaims(): Flow<List<Claim>> = session.whenSignedIn(emptyList()) { uid ->
        claims.whereEqualTo("claimerId", uid).liveList().map { docs -> docs.map { it.toClaim() } }
    }

    override suspend fun claim(wish: Wish) {
        val uid = session.requireUid()
        require(wish.ownerId != uid) { "You can't claim your own wish." }
        val wishDoc = firestore().collection("wishes").document(wish.id).get().await()
        val viewers = (wishDoc.get("viewerIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val myName = firestore().collection("users").document(uid).get().await().getString("displayName").orEmpty()
        claims.document("${wish.id}_$uid").set(
            mapOf(
                "wishId" to wish.id, "wishOwnerId" to wish.ownerId, "claimerId" to uid, "claimerName" to myName,
                "viewerIds" to viewers, "status" to "reserved", "quantity" to 1,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun release(wishId: String) {
        claims.document("${wishId}_${session.requireUid()}").delete().await()
    }
}

class FirebaseOccasionRepository @Inject constructor(private val session: FirebaseSession) : OccasionRepository {
    private val occasions get() = firestore().collection("occasions")

    override fun occasions(): Flow<List<Occasion>> = session.whenSignedIn(emptyList()) { uid ->
        combine(
            occasions.whereEqualTo("ownerId", uid).liveList(),
            occasions.whereArrayContains("viewerIds", uid).liveList(),
        ) { mine, shared -> (mine + shared).distinctBy { it.id }.map { it.toOccasion() } }
    }

    override suspend fun save(occasion: Occasion): String {
        val uid = session.requireUid()
        val ref = if (occasion.id.isEmpty()) occasions.document() else occasions.document(occasion.id)
        val data = mutableMapOf<String, Any?>(
            "ownerId" to uid,
            "personId" to (occasion.personId ?: uid),
            "kind" to occasion.kind.name.lowercase(),
            "title" to occasion.title,
            "month" to occasion.month,
            "day" to occasion.day,
            "year" to occasion.year,
            "recurrence" to if (occasion.yearly) "yearly" else "once",
            "visibleCircleIds" to occasion.visibleCircleIds,
        )
        if (occasion.id.isEmpty()) {
            data["viewerIds"] = emptyList<String>()
            ref.set(data).await()
        } else {
            ref.update(data).await()
        }
        return ref.id
    }

    override suspend fun delete(occasionId: String) {
        occasions.document(occasionId).delete().await()
    }
}
