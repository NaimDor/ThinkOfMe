package com.thinkofme.feature.wishes

import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.thinkofme.core.designsystem.component.EmptyState
import com.thinkofme.core.designsystem.component.IconCircleButton
import com.thinkofme.core.designsystem.component.PrimaryButton
import com.thinkofme.core.designsystem.component.WrapStage
import com.thinkofme.core.designsystem.component.pressable
import com.thinkofme.core.designsystem.icon.AppIcons
import com.thinkofme.core.designsystem.theme.AppSpace
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.model.UpcomingOccasion
import com.thinkofme.core.ui.AboutMeCard
import com.thinkofme.core.ui.PersonAvatar
import com.thinkofme.core.ui.R
import com.thinkofme.core.ui.WishCard
import com.thinkofme.core.ui.WishImage
import com.thinkofme.core.ui.ads.InlineBanner
import com.thinkofme.core.ui.ads.LocalAdsConfig
import com.thinkofme.core.ui.ads.NativeAdCard
import com.thinkofme.core.ui.formatMoney
import com.thinkofme.core.ui.occasionTitle
import com.thinkofme.core.ui.priorityLabel
import com.thinkofme.core.ui.relativeDays
import kotlinx.serialization.Serializable

@Serializable data class PersonRoute(val personId: String)

@Serializable data class WishRoute(val wishId: String, val ownerId: String)

@Serializable data object MyListRoute

@Serializable data class EditWishRoute(val wishId: String? = null, val sharedText: String? = null)

fun NavGraphBuilder.wishesScreens(
    onBack: () -> Unit,
    onOpenWish: (wishId: String, ownerId: String) -> Unit,
    onEditWish: (wishId: String?) -> Unit,
) {
    composable<PersonRoute> {
        val vm: PersonViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        PersonScreen(state, onBack, onOpenWish, onAdd = { onEditWish(null) })
    }
    composable<WishRoute> {
        val vm: WishViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        WishScreen(state, onBack, vm::toggleClaim, onEdit = { state.wish?.let { onEditWish(it.id) } }, vm::markReceived, onDelete = { vm.delete(onBack) })
    }
    composable<MyListRoute> {
        val vm: MyListViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        MyListScreen(state, onOpenWish, onAdd = { onEditWish(null) })
    }
    composable<EditWishRoute> {
        val vm: EditWishViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        if (state.saved) androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        EditWishScreen(state, vm, onBack)
    }
}

// ---------- shared chrome ----------

@Composable
internal fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = AppSpace.l, vertical = AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconCircleButton(AppIcons.Back, stringResource(R.string.action_back), onBack) else Spacer(Modifier.size(44.dp))
        Text(title, style = AppTheme.type.headline, color = AppTheme.colors.ink, modifier = Modifier.weight(1f).padding(horizontal = AppSpace.s), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { actions() }
    }
}

// ---------- person ----------

@Composable
fun PersonScreen(
    state: PersonUiState,
    onBack: () -> Unit,
    onOpenWish: (String, String) -> Unit,
    onAdd: () -> Unit,
) {
    val person = state.person
    val ads = LocalAdsConfig.current
    Column(Modifier.fillMaxSize().background(AppTheme.colors.canvas)) {
        TopBar(title = "", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = AppSpace.l, end = AppSpace.l, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpace.l),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpace.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.m)) {
                        PersonAvatar(person, size = 64.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(person?.displayName.orEmpty(), style = AppTheme.type.displayLarge, color = AppTheme.colors.ink)
                            state.nextOccasion?.let { NextLine(it, state.isMe) }
                        }
                    }
                    if (!state.isMe && person != null) AboutMeCard(person.aboutMe)
                }
            }
            if (!state.loading && state.wishes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (state.isMe) {
                        EmptyState(stringResource(R.string.my_list_empty_title), stringResource(R.string.my_list_empty_body), action = { PrimaryButton(stringResource(R.string.action_add_wish), onAdd, leadingIcon = AppIcons.Plus) })
                    } else {
                        EmptyState(stringResource(R.string.person_empty_title), stringResource(R.string.person_empty_body, person?.displayName.orEmpty()))
                    }
                }
            }
            state.wishes.forEachIndexed { index, wish ->
                item(key = wish.id) {
                    WishCard(wish, onClick = { onOpenWish(wish.id, wish.ownerId) }, wrapped = !state.isMe && wish.id in state.wrappedIds)
                }
                // Native ads never in the first row, at most one per 12 wishes.
                if (ads.enabled && index > 0 && (index + 1) % 12 == 0) {
                    item(key = "ad_$index", span = { GridItemSpan(maxLineSpan) }) { NativeAdCard() }
                }
            }
        }
    }
}

@Composable
private fun NextLine(upcoming: UpcomingOccasion, isMe: Boolean) {
    // The name is already the page title, so birthdays just say "Birthday".
    val title = if (upcoming.occasion.kind == com.thinkofme.core.model.OccasionKind.BIRTHDAY) {
        stringResource(R.string.kind_birthday_title)
    } else {
        occasionTitle(upcoming, if (isMe) upcoming.person?.id else null)
    }
    Text("$title · ${relativeDays(upcoming.daysUntil)}", style = AppTheme.type.callout, color = AppTheme.colors.inkMuted)
}

// ---------- wish detail ----------

@Composable
fun WishScreen(
    state: WishUiState,
    onBack: () -> Unit,
    onToggleClaim: () -> Unit,
    onEdit: () -> Unit,
    onMarkReceived: () -> Unit,
    onDelete: () -> Unit,
) {
    val wish = state.wish ?: run {
        Box(Modifier.fillMaxSize().background(AppTheme.colors.canvas)) { TopBar("", onBack) }
        return
    }
    val owner = state.owner
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val wrapped = state.myClaim != null
    val someoneElse = state.claims.firstOrNull { it.claimerId != state.myClaim?.claimerId }

    Box(Modifier.fillMaxSize().background(AppTheme.colors.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
            TopBar(
                title = owner?.let { stringResource(R.string.wishes_of, it.displayName) }.orEmpty(),
                onBack = onBack,
                actions = {
                    if (state.isMine) {
                        Box {
                            IconCircleButton(AppIcons.More, stringResource(R.string.action_edit), { menu = true })
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onEdit() }, leadingIcon = { Icon(AppIcons.Edit, null) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.mark_received)) }, onClick = { menu = false; onMarkReceived() }, leadingIcon = { Icon(AppIcons.Check, null) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; confirmDelete = true }, leadingIcon = { Icon(AppIcons.Trash, null) })
                            }
                        }
                    } else {
                        IconCircleButton(AppIcons.Share, stringResource(R.string.action_share), {
                            val text = listOfNotNull(wish.title, wish.urls.firstOrNull()).joinToString("\n")
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        })
                    }
                },
            )
            Column(Modifier.padding(horizontal = AppSpace.l), verticalArrangement = Arrangement.spacedBy(AppSpace.s)) {
                WrapStage(wrapped = wrapped, modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    WishImage(wish, Modifier.fillMaxSize(), iconSize = 120.dp)
                }
                Spacer(Modifier.height(AppSpace.xs))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
                    PersonAvatar(owner, size = 26.dp)
                    Text(listOfNotNull(owner?.displayName, priorityLabel(wish.priority)).joinToString(" · "), style = AppTheme.type.callout, color = AppTheme.colors.inkMuted)
                }
                Text(wish.title, style = AppTheme.type.displayMedium, color = AppTheme.colors.ink)
                val meta = listOfNotNull(wish.price?.let { formatMoney(it) }, wish.store).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = AppTheme.type.body, color = AppTheme.colors.inkMuted)
                wish.note?.let {
                    Text("“$it”", style = AppTheme.type.title.copy(fontStyle = FontStyle.Italic, fontWeight = AppTheme.type.body.fontWeight), color = AppTheme.colors.ink, modifier = Modifier.padding(top = AppSpace.xs))
                }
                if (!state.isMine) {
                    val ownerName = owner?.displayName.orEmpty()
                    val hint = when {
                        wrapped -> stringResource(R.string.claim_hint_mine, ownerName)
                        someoneElse != null -> stringResource(R.string.claim_hint_other, someoneElse.claimerName, ownerName)
                        else -> stringResource(R.string.claim_hint_open, ownerName)
                    }
                    Row(Modifier.padding(top = AppSpace.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
                        Icon(AppIcons.Lock, null, tint = AppTheme.colors.inkMuted, modifier = Modifier.size(16.dp))
                        Text(hint, style = AppTheme.type.caption, color = AppTheme.colors.inkMuted)
                    }
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(AppTheme.colors.canvas)
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = AppSpace.l, vertical = AppSpace.m),
            horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
        ) {
            wish.urls.firstOrNull()?.let { url ->
                Box(
                    Modifier.size(56.dp).background(AppTheme.colors.surfaceMuted, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .pressable(onClick = { runCatching { uri.openUri(url) } }, onClickLabel = stringResource(R.string.open_in_store)),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.External, stringResource(R.string.open_in_store), tint = AppTheme.colors.ink, modifier = Modifier.size(20.dp)) }
            }
            when {
                state.isMine -> PrimaryButton(stringResource(R.string.action_edit), onEdit, Modifier.weight(1f), leadingIcon = AppIcons.Edit, container = AppTheme.colors.surfaceMuted, content = AppTheme.colors.ink)
                wrapped -> PrimaryButton(stringResource(R.string.claim_undo), onToggleClaim, Modifier.weight(1f), loading = state.busy, leadingIcon = AppIcons.Check, container = AppTheme.colors.ribbon, content = AppTheme.colors.onRibbon)
                else -> PrimaryButton(stringResource(R.string.claim_action), onToggleClaim, Modifier.weight(1f), loading = state.busy, leadingIcon = AppIcons.Gift)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_wish_title)) },
            text = { Text(stringResource(R.string.delete_wish_body)) },
            confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text(stringResource(R.string.action_delete), color = AppTheme.colors.danger) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = AppTheme.colors.surface,
        )
    }
}

// ---------- my list ----------

@Composable
fun MyListScreen(state: MyListUiState, onOpenWish: (String, String) -> Unit, onAdd: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(AppTheme.colors.canvas),
        contentPadding = PaddingValues(start = AppSpace.l, end = AppSpace.l, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpace.l),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.my_list_title),
                style = AppTheme.type.displayLarge,
                color = AppTheme.colors.ink,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSpace.l, bottom = AppSpace.xs),
            )
        }
        if (!state.loading && state.wishes.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(stringResource(R.string.my_list_empty_title), stringResource(R.string.my_list_empty_body), action = { PrimaryButton(stringResource(R.string.action_add_wish), onAdd, leadingIcon = AppIcons.Plus) })
            }
        }
        // Your own list never shows whether anything was claimed.
        items(state.wishes, key = { it.id }) { wish -> WishCard(wish, onClick = { onOpenWish(wish.id, wish.ownerId) }) }
        if (state.wishes.size >= 8) item(span = { GridItemSpan(maxLineSpan) }) { InlineBanner(Modifier.padding(top = AppSpace.s)) }
    }
}
