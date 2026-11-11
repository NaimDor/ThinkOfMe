package com.thinkofme.core.data

import com.thinkofme.core.model.AboutMe
import com.thinkofme.core.model.Circle
import com.thinkofme.core.model.CircleKind
import com.thinkofme.core.model.Claim
import com.thinkofme.core.model.Occasion
import com.thinkofme.core.model.Person
import com.thinkofme.core.model.SessionUser
import com.thinkofme.core.model.Wish
import com.thinkofme.core.model.WishDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Data contracts used by the features. Each has a Firebase implementation and
 * an in-memory demo implementation (used when the app is built without a
 * Firebase config, and for previews and tests).
 */
interface AuthRepository {
    /** Null while signed out. */
    val session: StateFlow<SessionUser?>
    val isDemo: Boolean

    suspend fun signInWithGoogleIdToken(idToken: String)
    suspend fun signInWithDemoData()
    suspend fun signOut()
    suspend fun deleteAccount()
}

interface ProfileRepository {
    /** The signed-in user's profile, or null if onboarding hasn't created it yet. */
    fun me(): Flow<Person?>

    /** Everyone you share a circle with, keyed by id. */
    fun people(): Flow<Map<String, Person>>

    suspend fun saveProfile(displayName: String, aboutMe: AboutMe)

    fun remindersEnabled(): Flow<Boolean>
    suspend fun setRemindersEnabled(enabled: Boolean)

    /** Saves time zone and locale so reminders arrive at a sensible local hour. */
    suspend fun saveDeviceSettings(timeZone: String, locale: String, pushToken: String?)
}

data class Invite(val code: String, val url: String, val expiresAtMillis: Long)

data class InvitePreview(val circleName: String, val kind: CircleKind, val inviterName: String, val memberCount: Int)

interface CircleRepository {
    fun circles(): Flow<List<Circle>>
    suspend fun create(name: String, kind: CircleKind): String
    suspend fun rename(circleId: String, name: String)
    suspend fun createInvite(circleId: String): Invite
    suspend fun previewInvite(code: String): InvitePreview
    suspend fun joinWithInvite(code: String): String
    suspend fun leave(circleId: String)
}

data class LinkPreview(
    val url: String,
    val title: String?,
    val imageUrls: List<String>,
    val siteName: String?,
    val priceAmount: Double?,
    val priceCurrency: String?,
)

interface WishRepository {
    fun myWishes(): Flow<List<Wish>>

    /** Every wish other people shared with you. */
    fun visibleWishes(): Flow<List<Wish>>
    fun wishesOf(personId: String): Flow<List<Wish>>
    fun wish(wishId: String): Flow<Wish?>
    suspend fun save(wishId: String?, draft: WishDraft): String
    suspend fun markReceived(wishId: String)
    suspend fun delete(wishId: String)
    suspend fun unfurl(url: String): LinkPreview
}

interface ClaimRepository {
    /** Claims on a person's wishes, by wish id. Always empty for your own wishes. */
    fun claimsOnWishesOf(personId: String): Flow<Map<String, List<Claim>>>
    fun myClaims(): Flow<List<Claim>>
    suspend fun claim(wish: Wish)
    suspend fun release(wishId: String)
}

interface OccasionRepository {
    /** Your occasions plus those shared with you. */
    fun occasions(): Flow<List<Occasion>>
    suspend fun save(occasion: Occasion): String
    suspend fun delete(occasionId: String)
}

/** Clock indirection so date logic is testable. */
fun interface Today {
    operator fun invoke(): LocalDate
}
