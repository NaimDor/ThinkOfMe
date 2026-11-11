package com.thinkofme.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The signed-in person. */
data class SessionUser(
    val id: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
)

/** A person you share at least one circle with (or yourself). */
data class Person(
    val id: String,
    val displayName: String,
    val photoUrl: String? = null,
    val hueIndex: Int = 0,
    val aboutMe: AboutMe = AboutMe(),
) {
    val initial: String get() = displayName.trim().firstOrNull()?.uppercase() ?: "?"
}

/** Sizes and preferences that help people pick a gift. */
data class AboutMe(
    val clothingSize: String? = null,
    val shoeSize: String? = null,
    val ringSize: String? = null,
    val favoriteColors: String? = null,
    val allergies: String? = null,
    val dontBuy: String? = null,
) {
    val isEmpty: Boolean
        get() = listOf(clothingSize, shoeSize, ringSize, favoriteColors, allergies, dontBuy).all { it.isNullOrBlank() }
}

enum class CircleKind { PAIR, GROUP }

data class Circle(
    val id: String,
    val name: String,
    val kind: CircleKind,
    val ownerId: String,
    val memberIds: List<String>,
    val adminIds: List<String>,
)

enum class WishKind { PRODUCT, IDEA }

enum class WishStatus { ACTIVE, RECEIVED, ARCHIVED }

/** 1 = nice to have, 2 = would love, 3 = dream gift. */
enum class Priority(val level: Int) {
    NICE(1), LOVE(2), DREAM(3);

    companion object {
        fun of(level: Int): Priority = entries.firstOrNull { it.level == level } ?: LOVE
    }
}

data class Money(val amount: Double, val currency: String)

data class Wish(
    val id: String,
    val ownerId: String,
    val title: String,
    val note: String? = null,
    val urls: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val price: Money? = null,
    val store: String? = null,
    val kind: WishKind = WishKind.PRODUCT,
    val priority: Priority = Priority.LOVE,
    val quantity: Int = 1,
    val status: WishStatus = WishStatus.ACTIVE,
    val visibleCircleIds: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
)

/** Fields the user edits when adding or changing a wish. */
data class WishDraft(
    val title: String = "",
    val note: String = "",
    val url: String = "",
    val imageUrls: List<String> = emptyList(),
    val priceText: String = "",
    val currency: String = "USD",
    val store: String = "",
    val kind: WishKind = WishKind.PRODUCT,
    val priority: Priority = Priority.LOVE,
    val visibleCircleIds: List<String> = emptyList(),
) {
    val isValid: Boolean get() = title.isNotBlank() && title.length <= 140
}

enum class ClaimStatus { RESERVED, PURCHASED }

/** "I'm getting this". Never visible to the wish's owner. */
data class Claim(
    val wishId: String,
    val wishOwnerId: String,
    val claimerId: String,
    val claimerName: String,
    val status: ClaimStatus,
    val quantity: Int = 1,
)

enum class OccasionKind { BIRTHDAY, ANNIVERSARY, HOLIDAY, CUSTOM }

data class Occasion(
    val id: String,
    val ownerId: String,
    val personId: String?,
    val kind: OccasionKind,
    val title: String,
    val month: Int,
    val day: Int,
    val year: Int? = null,
    val yearly: Boolean = true,
    val visibleCircleIds: List<String> = emptyList(),
) {
    /** Next date this occasion lands on, or null for a one-off that has passed. */
    fun nextDate(today: LocalDate): LocalDate? {
        fun on(year: Int): LocalDate {
            val monthLength = java.time.YearMonth.of(year, month).lengthOfMonth()
            return LocalDate.of(year, month, day.coerceAtMost(monthLength))
        }
        if (!yearly) return year?.let { y -> on(y).takeIf { !it.isBefore(today) } }
        val thisYear = on(today.year)
        return if (thisYear.isBefore(today)) on(today.year + 1) else thisYear
    }

    fun daysUntil(today: LocalDate): Long? = nextDate(today)?.let { ChronoUnit.DAYS.between(today, it) }

    /** Age turned on the next occurrence, when the birth year is known. */
    fun turningAge(today: LocalDate): Int? {
        val next = nextDate(today) ?: return null
        return if (kind == OccasionKind.BIRTHDAY && year != null && yearly) next.year - year else null
    }
}

/** An occasion paired with whose it is and when it lands next. */
data class UpcomingOccasion(
    val occasion: Occasion,
    val person: Person?,
    val date: LocalDate,
    val daysUntil: Long,
)

/** Home-screen summary of someone in your circles. */
data class PersonSummary(
    val person: Person,
    val nextOccasion: UpcomingOccasion?,
    val newWishCount: Int,
    val openWishCount: Int,
    val wishCount: Int,
)
