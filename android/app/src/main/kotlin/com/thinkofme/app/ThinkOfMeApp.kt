package com.thinkofme.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thinkofme.core.designsystem.component.pressable
import com.thinkofme.core.designsystem.icon.AppIcons
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.ui.R
import com.thinkofme.feature.account.OnboardingScreen
import com.thinkofme.feature.account.SettingsRoute
import com.thinkofme.feature.account.SignInScreen
import com.thinkofme.feature.account.settingsScreen
import com.thinkofme.feature.circles.CirclesRoute
import com.thinkofme.feature.circles.JoinRoute
import com.thinkofme.feature.circles.circlesScreens
import com.thinkofme.feature.home.HomeRoute
import com.thinkofme.feature.home.homeScreen
import com.thinkofme.feature.occasions.OccasionsRoute
import com.thinkofme.feature.occasions.occasionsScreen
import com.thinkofme.feature.wishes.EditWishRoute
import com.thinkofme.feature.wishes.MyListRoute
import com.thinkofme.feature.wishes.PersonRoute
import com.thinkofme.feature.wishes.WishRoute
import com.thinkofme.feature.wishes.wishesScreens
import kotlinx.coroutines.flow.Flow

/** Something the app was opened with: a shared link or an invite. */
sealed interface IncomingIntent {
    data class SharedText(val text: String) : IncomingIntent
    data class Invite(val code: String) : IncomingIntent
}

@Composable
fun ThinkOfMeApp(
    incoming: Flow<IncomingIntent>,
    onIncomingHandled: () -> Unit,
    webClientId: String?,
    onPrivacyOptions: (() -> Unit)?,
) {
    val vm: AppViewModel = hiltViewModel()
    val gate by vm.gate.collectAsStateWithLifecycle()

    Crossfade(gate, label = "gate", modifier = Modifier.background(AppTheme.colors.canvas)) { current ->
        when (current) {
            AppGate.Loading -> Box(Modifier.fillMaxSize().background(AppTheme.colors.canvas))
            AppGate.SignedOut -> SignInScreen(webClientId)
            AppGate.NeedsProfile -> OnboardingScreen()
            AppGate.Ready -> {
                LaunchedEffect(Unit) { vm.registerDevice() }
                AskForNotifications()
                MainNavigation(incoming, onIncomingHandled, onPrivacyOptions)
            }
        }
    }
}

@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

private enum class Tab(val route: Any, val icon: ImageVector, val label: Int) {
    Home(HomeRoute, AppIcons.Home, R.string.tab_home),
    MyList(MyListRoute, AppIcons.Heart, R.string.tab_my_list),
    Circles(CirclesRoute, AppIcons.People, R.string.tab_circles),
    Dates(OccasionsRoute, AppIcons.Calendar, R.string.tab_dates),
}

@Composable
private fun MainNavigation(incoming: Flow<IncomingIntent>, onIncomingHandled: () -> Unit, onPrivacyOptions: (() -> Unit)?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = Tab.entries.firstOrNull { tab -> destination?.hasRoute(tab.route::class) == true }

    LaunchedEffect(Unit) {
        incoming.collect { intent ->
            when (intent) {
                is IncomingIntent.SharedText -> nav.navigate(EditWishRoute(sharedText = intent.text))
                is IncomingIntent.Invite -> nav.navigate(JoinRoute(intent.code))
            }
            onIncomingHandled()
        }
    }

    Box(Modifier.fillMaxSize().background(AppTheme.colors.canvas)) {
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.fillMaxSize()) {
            homeScreen(
                onOpenPerson = { nav.navigate(PersonRoute(it)) },
                onOpenProfile = { nav.navigate(SettingsRoute) },
                onCreateCircle = { nav.switchTab(Tab.Circles) },
            )
            wishesScreens(
                onBack = { nav.popBackStack() },
                onOpenWish = { id, owner -> nav.navigate(WishRoute(id, owner)) },
                onEditWish = { id -> nav.navigate(EditWishRoute(wishId = id)) },
            )
            circlesScreens(
                onBack = { nav.popBackStack() },
                onOpenPerson = { nav.navigate(PersonRoute(it)) },
                onJoinCode = { nav.navigate(JoinRoute(it)) },
            )
            occasionsScreen(onOpenPerson = { nav.navigate(PersonRoute(it)) })
            settingsScreen(onBack = { nav.popBackStack() }, onPrivacyOptions = onPrivacyOptions)
        }

        AnimatedVisibility(
            visible = currentTab != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(current = currentTab, onSelect = { nav.switchTab(it) }, onAdd = { nav.navigate(EditWishRoute()) })
        }
    }
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(current: Tab?, onSelect: (Tab) -> Unit, onAdd: () -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface).border(width = 1.dp, color = colors.hairline)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.take(2).forEach { TabItem(it, it == current, onSelect) }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(colors.accent).pressable(onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Plus, stringResource(R.string.action_add_wish), tint = colors.onAccent, modifier = Modifier.size(24.dp)) }
            Tab.entries.drop(2).forEach { TabItem(it, it == current, onSelect) }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onSelect: (Tab) -> Unit) {
    val color = if (selected) AppTheme.colors.accentText else AppTheme.colors.inkMuted
    Column(
        Modifier.width(64.dp).clip(CircleShape).pressable(onClick = { onSelect(tab) }, role = Role.Tab).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, null, tint = color, modifier = Modifier.size(24.dp))
        Text(stringResource(tab.label), style = AppTheme.type.tab, color = color, maxLines = 1)
    }
}
