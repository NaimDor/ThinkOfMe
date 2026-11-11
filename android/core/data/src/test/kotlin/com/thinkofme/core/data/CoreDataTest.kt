package com.thinkofme.core.data

import com.thinkofme.core.data.demo.DemoClaimRepository
import com.thinkofme.core.data.demo.DemoStore
import com.thinkofme.core.model.Occasion
import com.thinkofme.core.model.OccasionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoreDataTest {
    @Test
    fun `claims on your own wishes are never returned`() = runTest {
        val store = DemoStore()
        val claims = DemoClaimRepository(store)
        assertTrue(claims.claimsOnWishesOf(store.me).first().isEmpty())
        assertEquals("Noa", claims.claimsOnWishesOf("maya").first()["w1"]?.single()?.claimerName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `you cannot claim your own wish`() = runTest {
        val store = DemoStore()
        DemoClaimRepository(store).claim(store.wishes.value.first { it.ownerId == store.me })
    }

    @Test
    fun `next occurrence handles year wrap and leap days`() {
        val birthday = Occasion("o", "a", "a", OccasionKind.BIRTHDAY, "", 1, 1)
        assertEquals(1L, birthday.daysUntil(LocalDate.of(2026, 12, 31)))
        val leap = Occasion("o", "a", "a", OccasionKind.BIRTHDAY, "", 2, 29)
        assertEquals(LocalDate.of(2027, 2, 28), leap.nextDate(LocalDate.of(2027, 1, 10)))
        val once = Occasion("o", "a", "a", OccasionKind.CUSTOM, "Party", 1, 1, year = 2026, yearly = false)
        assertNull(once.nextDate(LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun `overview puts the nearest occasion first and excludes me`() {
        val store = DemoStore()
        val me = store.people.value.getValue(store.me)
        val visible = store.wishes.value.filter { it.ownerId != store.me }
        val overview = ObserveOverview.build(me, store.people.value, visible, store.occasions.value, LocalDate.now(), System.currentTimeMillis())
        assertEquals("maya", overview.nextUp?.person?.id)
        assertEquals(12L, overview.nextUp?.daysUntil)
        assertTrue(overview.people.none { it.person.id == store.me })
        assertEquals("maya", overview.people.first().person.id)
    }
}
