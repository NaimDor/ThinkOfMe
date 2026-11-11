package com.thinkofme.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.thinkofme.core.data.AuthRepository
import com.thinkofme.core.data.ClaimRepository
import com.thinkofme.core.data.ObserveOverview
import com.thinkofme.core.data.Overview
import com.thinkofme.core.designsystem.component.CountdownRing
import com.thinkofme.core.designsystem.component.Dot
import com.thinkofme.core.designsystem.component.EmptyState
import com.thinkofme.core.designsystem.component.Hairline
import com.thinkofme.core.designsystem.component.ListRow
import com.thinkofme.core.designsystem.component.PillButton
import com.thinkofme.core.designsystem.component.PrimaryButton
import com.thinkofme.core.designsystem.component.SectionHeader
import com.thinkofme.core.designsystem.component.pressable
import com.thinkofme.core.designsystem.theme.AppRadius
import com.thinkofme.core.designsystem.theme.AppSpace
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.model.PersonSummary
import com.thinkofme.core.ui.PersonAvatar
import com.thinkofme.core.ui.R
import com.thinkofme.core.ui.ads.LocalAdsConfig
import com.thinkofme.core.ui.ads.NativeAdCard
import com.thinkofme.core.ui.ads.nativeAdAfter
import com.thinkofme.core.ui.greeting
import com.thinkofme.core.ui.longDate
import com.thinkofme.core.ui.occasionTitle
import com.thinkofme.core.ui.shortDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import java.time.LocalDate
import javax.inject.Inject

@Serializable
data object HomeRoute

data class HomeUiState(
    val loading: Boolean = true,
    val overview: Overview? = null,
    /** Open (unclaimed) wishes of the next-up person. */
    val nextUpOpen: Int = 0,
    val isDemo: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeOverview: ObserveOverview,
    claims: ClaimRepository,
    auth: AuthRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = observeOverview()
        .flatMapLatest { overview ->
            val personId = overview.nextUp?.person?.id
            if (personId == null) {
                flowOf(HomeUiState(false, overview, 0, auth.isDemo))
            } else {
                claims.claimsOnWishesOf(personId).map { claimed ->
                    HomeUiState(false, overview, (overview.nextUpWishCount - claimed.keys.size).coerceAtLeast(0), auth.isDemo)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

fun NavGraphBuilder.homeScreen(
    onOpenPerson: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onCreateCircle: () -> Unit,
) {
    composable<HomeRoute> {
        val viewModel: HomeViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        HomeScreen(state, onOpenPerson, onOpenProfile, onCreateCircle)
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenPerson: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onCreateCircle: () -> Unit,
) {
    val overview = state.overview
    val ads = LocalAdsConfig.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppTheme.colors.canvas),
        contentPadding = PaddingValues(start = AppSpace.l, end = AppSpace.l, bottom = 120.dp),
    ) {
        item(key = "header") {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSpace.l)) {
                if (state.isDemo) DemoBanner()
                Header(overview, onOpenProfile)
                Spacer(Modifier.height(28.dp))
            }
        }

        val nextUp = overview?.nextUp
        if (nextUp != null) {
            item(key = "next") {
                FadeIn(0) {
                    NextUpCard(
                        title = occasionTitle(nextUp, overview.me?.id),
                        date = longDate(nextUp.date),
                        days = nextUp.daysUntil,
                        open = state.nextUpOpen,
                        total = overview.nextUpWishCount,
                        onView = { nextUp.person?.id?.let(onOpenPerson) },
                    )
                }
                Spacer(Modifier.height(AppSpace.xl))
            }
        }

        if (overview != null && overview.people.isEmpty() && !state.loading) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    action = { PrimaryButton(stringResource(R.string.home_empty_action), onCreateCircle) },
                )
            }
        } else if (overview != null) {
            item(key = "peopleHeader") { SectionHeader(stringResource(R.string.your_people)) }
            itemsIndexed(overview.people, key = { _, it -> it.person.id }) { index, summary ->
                FadeIn(index + 1) {
                    Column {
                        PersonRow(summary, onClick = { onOpenPerson(summary.person.id) })
                        if (index < overview.people.lastIndex) Hairline()
                    }
                }
                if (ads.nativeAdAfter(index)) NativeAdCard(Modifier.padding(vertical = AppSpace.xs))
            }
        }
    }
}

@Composable
private fun DemoBanner() {
    Text(
        stringResource(R.string.demo_banner),
        style = AppTheme.type.caption,
        color = AppTheme.colors.inkMuted,
        modifier = Modifier
            .padding(bottom = AppSpace.s)
            .clip(RoundedCornerShape(AppRadius.s))
            .background(AppTheme.colors.surfaceMuted)
            .padding(horizontal = AppSpace.s, vertical = 6.dp),
    )
}

@Composable
private fun Header(overview: Overview?, onOpenProfile: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(longDate(LocalDate.now()), style = AppTheme.type.caption, color = AppTheme.colors.inkMuted)
            Text(greeting(), style = AppTheme.type.displayLarge, color = AppTheme.colors.ink)
        }
        Box(Modifier.pressable(onOpenProfile)) { PersonAvatar(overview?.me, size = 44.dp) }
    }
}

@Composable
private fun NextUpCard(title: String, date: String, days: Long, open: Int, total: Int, onView: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.l))
            .background(colors.surfaceTint)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.m)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.next_up).uppercase(), style = AppTheme.type.overline, color = colors.accentText)
                Text(title, style = AppTheme.type.displayMedium, color = colors.ink)
                Text(date, style = AppTheme.type.callout, color = colors.inkMuted)
            }
            CountdownRing(
                days = days,
                label = stringResource(R.string.days_label),
                accessibilityLabel = pluralStringResource(R.plurals.days_to_go, days.toInt(), days.toInt()),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (total > 0) pluralStringResource(R.plurals.open_of_total, open, open, total) else "",
                style = AppTheme.type.callout,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            PillButton(stringResource(R.string.action_view_list), onView)
        }
    }
}

@Composable
private fun PersonRow(summary: PersonSummary, onClick: () -> Unit) {
    val colors = AppTheme.colors
    ListRow(
        onClick = onClick,
        leading = { PersonAvatar(summary.person) },
        title = summary.person.displayName,
        subtitle = {
            if (summary.newWishCount > 0) {
                Dot(colors.accent)
                Text(pluralStringResource(R.plurals.new_wishes, summary.newWishCount, summary.newWishCount), style = AppTheme.type.caption, color = colors.inkMuted)
            } else {
                summary.nextOccasion?.let { Text(com.thinkofme.core.ui.occasionKindName(it.occasion.kind).replaceFirstChar { c -> c.titlecase() }, style = AppTheme.type.caption, color = colors.inkMuted) }
            }
        },
        trailing = { summary.nextOccasion?.let { Text(shortDate(it.date), style = AppTheme.type.caption, color = colors.inkMuted) } },
    )
}

/** Gentle staggered entrance for list content. */
@Composable
private fun FadeIn(index: Int, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40L * index.coerceAtMost(8))
        alpha.animateTo(1f, tween(380))
    }
    Box(Modifier.graphicsLayer { this.alpha = alpha.value; translationY = (1f - alpha.value) * 8.dp.toPx() }) { content() }
}
