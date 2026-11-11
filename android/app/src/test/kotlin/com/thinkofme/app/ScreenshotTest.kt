package com.thinkofme.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.thinkofme.core.data.ObserveOverview
import com.thinkofme.core.data.demo.DemoStore
import com.thinkofme.core.designsystem.theme.ThinkOfMeTheme
import com.thinkofme.core.model.WishStatus
import com.thinkofme.feature.home.HomeScreen
import com.thinkofme.feature.home.HomeUiState
import com.thinkofme.feature.occasions.OccasionsScreen
import com.thinkofme.feature.occasions.OccasionsUiState
import com.thinkofme.feature.wishes.MyListScreen
import com.thinkofme.feature.wishes.MyListUiState
import com.thinkofme.feature.wishes.PersonScreen
import com.thinkofme.feature.wishes.PersonUiState
import com.thinkofme.feature.wishes.WishScreen
import com.thinkofme.feature.wishes.WishUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders the main screens with the demo world, in light, dark and every
 * launch language. Run `./gradlew :app:recordRoborazziDebug` to refresh the
 * images in app/build/outputs/roborazzi, `verifyRoborazziDebug` in CI.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val store = DemoStore()
    private val me = store.people.value.getValue(store.me)
    private val visible = store.wishes.value.filter { it.ownerId != store.me && store.me in store.viewersOf(it.ownerId, it.visibleCircleIds) }
    private val overview = ObserveOverview.build(me, store.people.value, visible, store.occasions.value, LocalDate.now(), System.currentTimeMillis())
    private val homeState = HomeUiState(loading = false, overview = overview, nextUpOpen = 3, isDemo = false)

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent { ThinkOfMeTheme { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun home() = snap("home_light") { HomeScreen(homeState, {}, {}, {}) }

    @Config(qualifiers = "+night")
    @Test fun homeDark() = snap("home_dark") { HomeScreen(homeState, {}, {}, {}) }

    @Config(qualifiers = "+iw")
    @Test fun homeHebrew() = snap("home_he") { HomeScreen(homeState, {}, {}, {}) }

    @Config(qualifiers = "+ar")
    @Test fun homeArabic() = snap("home_ar") { HomeScreen(homeState, {}, {}, {}) }

    @Config(qualifiers = "+ru")
    @Test fun homeRussian() = snap("home_ru") { HomeScreen(homeState, {}, {}, {}) }

    private val maya = store.people.value.getValue("maya")
    private val mayaWishes = visible.filter { it.ownerId == "maya" && it.status == WishStatus.ACTIVE }
    private val personState = PersonUiState(
        loading = false, person = maya, isMe = false, wishes = mayaWishes, wrappedIds = setOf("w1"),
        nextOccasion = overview.upcoming.firstOrNull { it.person?.id == "maya" },
    )

    @Test fun person() = snap("person_light") { PersonScreen(personState, {}, { _, _ -> }, {}) }

    @Config(qualifiers = "+night")
    @Test fun personDark() = snap("person_dark") { PersonScreen(personState, {}, { _, _ -> }, {}) }

    private val headphones = store.wishes.value.first { it.id == "w1" }
    private val wishOpen = WishUiState(loading = false, wish = headphones, owner = maya, isMine = false)
    private val wishWrapped = wishOpen.copy(claims = store.claims.value.filter { it.wishId == "w1" }.map { it.copy(claimerId = store.me) }, myClaim = store.claims.value.first { it.wishId == "w1" }.copy(claimerId = store.me))

    @Test fun wish() = snap("wish_open") { WishScreen(wishOpen, {}, {}, {}, {}, {}) }

    @Test fun wishWrapped() = snap("wish_wrapped") { WishScreen(wishWrapped, {}, {}, {}, {}, {}) }

    @Config(qualifiers = "+night")
    @Test fun wishWrappedDark() = snap("wish_wrapped_dark") { WishScreen(wishWrapped, {}, {}, {}, {}, {}) }

    @Test fun myList() = snap("my_list") {
        MyListScreen(MyListUiState(false, store.wishes.value.filter { it.ownerId == store.me }, me), { _, _ -> }, {})
    }

    private fun holidays(language: String) = com.thinkofme.core.model.Holidays.all
        .filter { it.id in com.thinkofme.core.model.Holidays.defaultsFor(language) }
        .mapNotNull { h -> h.nextDate(LocalDate.now())?.let { h to it } }
        .sortedBy { it.second }

    @Test fun dates() = snap("dates") {
        OccasionsScreen(OccasionsUiState(false, overview.upcoming, store.people.value, store.circles.value, store.me, holidays("en")), {}, {})
    }

    @Config(qualifiers = "+iw")
    @Test fun datesHebrew() = snap("dates_he") {
        OccasionsScreen(OccasionsUiState(false, overview.upcoming, store.people.value, store.circles.value, store.me, holidays("he")), {}, {})
    }

    @Config(qualifiers = "+ar")
    @Test fun datesArabic() = snap("dates_ar") {
        OccasionsScreen(OccasionsUiState(false, overview.upcoming, store.people.value, store.circles.value, store.me, holidays("ar")), {}, {})
    }
}
