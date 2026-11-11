package com.thinkofme.core.data

import com.thinkofme.core.model.Occasion
import com.thinkofme.core.model.Person
import com.thinkofme.core.model.PersonSummary
import com.thinkofme.core.model.UpcomingOccasion
import com.thinkofme.core.model.Wish
import com.thinkofme.core.model.WishStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

private const val NEW_WISH_WINDOW_MS = 14 * 86_400_000L

/** Everything the Home screen shows, derived from the repositories. */
data class Overview(
    val me: Person?,
    val nextUp: UpcomingOccasion?,
    val nextUpOpenWishes: Int,
    val nextUpWishCount: Int,
    val people: List<PersonSummary>,
    val upcoming: List<UpcomingOccasion>,
)

class ObserveOverview @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
    private val wishes: WishRepository,
    private val occasions: OccasionRepository,
    private val claims: ClaimRepository,
    private val today: Today,
) {
    operator fun invoke(): Flow<Overview> = combine(
        profiles.me(),
        profiles.people(),
        wishes.visibleWishes(),
        occasions.occasions(),
        claims.myClaims(),
    ) { me, people, visible, occasionList, _ ->
        build(me, people, visible, occasionList, today(), System.currentTimeMillis())
    }

    companion object {
        fun upcoming(occasions: List<Occasion>, people: Map<String, Person>, today: LocalDate): List<UpcomingOccasion> =
            occasions.mapNotNull { occasion ->
                val date = occasion.nextDate(today) ?: return@mapNotNull null
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                UpcomingOccasion(occasion, people[occasion.personId ?: occasion.ownerId], date, days)
            }.sortedBy { it.daysUntil }

        fun build(
            me: Person?,
            people: Map<String, Person>,
            visible: List<Wish>,
            occasions: List<Occasion>,
            today: LocalDate,
            nowMillis: Long,
        ): Overview {
            val upcoming = upcoming(occasions, people, today)
            val others = people.values.filter { it.id != me?.id }
            val active = visible.filter { it.status == WishStatus.ACTIVE }
            val summaries = others.map { person ->
                val theirs = active.filter { it.ownerId == person.id }
                PersonSummary(
                    person = person,
                    nextOccasion = upcoming.firstOrNull { (it.occasion.personId ?: it.occasion.ownerId) == person.id },
                    newWishCount = theirs.count { nowMillis - it.createdAtMillis < NEW_WISH_WINDOW_MS },
                    openWishCount = theirs.size,
                    wishCount = theirs.size,
                )
            }.sortedWith(compareBy({ it.nextOccasion?.daysUntil ?: Long.MAX_VALUE }, { it.person.displayName }))

            val nextUp = upcoming.firstOrNull { it.person?.id != me?.id }
            val nextUpWishes = nextUp?.person?.id?.let { id -> active.filter { it.ownerId == id } }.orEmpty()
            return Overview(
                me = me,
                nextUp = nextUp,
                nextUpOpenWishes = nextUpWishes.size,
                nextUpWishCount = nextUpWishes.size,
                people = summaries,
                upcoming = upcoming,
            )
        }
    }
}
