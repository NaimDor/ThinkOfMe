package com.thinkofme.feature.circles

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.thinkofme.core.data.CircleRepository
import com.thinkofme.core.data.InvitePreview
import com.thinkofme.core.data.ProfileRepository
import com.thinkofme.core.designsystem.component.AppTextField
import com.thinkofme.core.designsystem.component.ChoiceChip
import com.thinkofme.core.designsystem.component.EmptyState
import com.thinkofme.core.designsystem.component.GiftBoxMoment
import com.thinkofme.core.designsystem.component.Hairline
import com.thinkofme.core.designsystem.component.IconCircleButton
import com.thinkofme.core.designsystem.component.ListRow
import com.thinkofme.core.designsystem.component.PrimaryButton
import com.thinkofme.core.designsystem.component.SecondaryButton
import com.thinkofme.core.designsystem.component.TextAction
import com.thinkofme.core.designsystem.component.pressable
import com.thinkofme.core.designsystem.icon.AppIcons
import com.thinkofme.core.designsystem.theme.AppSpace
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.model.Circle
import com.thinkofme.core.model.CircleKind
import com.thinkofme.core.model.Person
import com.thinkofme.core.ui.PersonAvatar
import com.thinkofme.core.ui.R
import com.thinkofme.core.ui.ads.InlineBanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable data object CirclesRoute

@Serializable data class JoinRoute(val code: String)

data class CirclesUiState(
    val loading: Boolean = true,
    val circles: List<Circle> = emptyList(),
    val people: Map<String, Person> = emptyMap(),
    val busy: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class CirclesViewModel @Inject constructor(private val repo: CircleRepository, profiles: ProfileRepository) : ViewModel() {
    private val transient = MutableStateFlow(false to false)
    val state: StateFlow<CirclesUiState> = combine(repo.circles(), profiles.people(), transient) { circles, people, (busy, error) ->
        CirclesUiState(false, circles, people, busy, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CirclesUiState())

    fun create(name: String, kind: CircleKind, then: (String) -> Unit) = launch { then(repo.create(name, kind)) }

    /** Creates an invite link and hands it to the share sheet. */
    fun invite(circleId: String, share: (String) -> Unit) = launch { share(repo.createInvite(circleId).url) }

    fun leave(circleId: String) = launch { repo.leave(circleId) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            transient.value = true to false
            val ok = runCatching { block() }.isSuccess
            transient.value = false to !ok
        }
    }
}

data class JoinUiState(val preview: InvitePreview? = null, val loading: Boolean = true, val joining: Boolean = false, val joined: Boolean = false, val error: Boolean = false)

@HiltViewModel
class JoinViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: CircleRepository) : ViewModel() {
    private val code = savedState.toRoute<JoinRoute>().code
    private val _state = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val preview = runCatching { repo.previewInvite(code) }.getOrNull()
            _state.update { it.copy(preview = preview, loading = false, error = preview == null) }
        }
    }

    fun join() {
        viewModelScope.launch {
            _state.update { it.copy(joining = true) }
            val ok = runCatching { repo.joinWithInvite(code) }.isSuccess
            _state.update { it.copy(joining = false, joined = ok, error = !ok) }
        }
    }
}

fun NavGraphBuilder.circlesScreens(onBack: () -> Unit, onOpenPerson: (String) -> Unit, onJoinCode: (String) -> Unit) {
    composable<CirclesRoute> {
        val vm: CirclesViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        CirclesScreen(state, vm, onOpenPerson, onJoinCode)
    }
    composable<JoinRoute> {
        val vm: JoinViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        if (state.joined) LaunchedEffect(Unit) { onBack() }
        JoinScreen(state, vm::join, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CirclesScreen(state: CirclesUiState, vm: CirclesViewModel, onOpenPerson: (String) -> Unit, onJoinCode: (String) -> Unit) {
    val context = LocalContext.current
    var creating by rememberSaveable { mutableStateOf(false) }
    var joining by rememberSaveable { mutableStateOf(false) }
    val inviteText = stringResource(R.string.invite_message, "%s")

    fun share(url: String) {
        val text = inviteText.replace("%s", url)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }

    LazyColumn(
        Modifier.fillMaxSize().background(AppTheme.colors.canvas),
        contentPadding = PaddingValues(start = AppSpace.l, end = AppSpace.l, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSpace.l, bottom = AppSpace.m), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.circles_title), style = AppTheme.type.displayLarge, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
                IconCircleButton(AppIcons.Plus, stringResource(R.string.create_circle), { creating = true })
            }
        }
        if (!state.loading && state.circles.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.circles_empty_title),
                    stringResource(R.string.circles_empty_body),
                    action = { PrimaryButton(stringResource(R.string.create_circle), { creating = true }, leadingIcon = AppIcons.Plus) },
                )
            }
        }
        itemsIndexed(state.circles, key = { _, c -> c.id }) { _, circle ->
            CircleCard(circle, state.people, onInvite = { vm.invite(circle.id, ::share) }, onOpenPerson = onOpenPerson)
            Spacer(Modifier.height(AppSpace.m))
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = AppSpace.xs), contentAlignment = Alignment.Center) {
                TextAction(stringResource(R.string.join_with_code), { joining = true })
            }
        }
        if (state.circles.size >= 4) item { InlineBanner(Modifier.padding(top = AppSpace.l)) }
    }

    if (creating) {
        ModalBottomSheet(onDismissRequest = { creating = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppTheme.colors.canvas) {
            CreateCircleSheet(onCreate = { name, kind -> vm.create(name, kind) { id -> creating = false; vm.invite(id, ::share) } })
        }
    }
    if (joining) {
        ModalBottomSheet(onDismissRequest = { joining = false }, containerColor = AppTheme.colors.canvas) {
            var code by rememberSaveable { mutableStateOf("") }
            Column(Modifier.padding(AppSpace.l).windowInsetsPadding(WindowInsets.navigationBars), verticalArrangement = Arrangement.spacedBy(AppSpace.m)) {
                Text(stringResource(R.string.join_with_code), style = AppTheme.type.title, color = AppTheme.colors.ink)
                AppTextField(code, { code = it.uppercase().take(12) }, label = stringResource(R.string.invite_code, "").trimEnd(':', ' '),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                PrimaryButton(stringResource(R.string.join), { joining = false; onJoinCode(code) }, Modifier.fillMaxWidth(), enabled = code.length >= 8)
            }
        }
    }
}

@Composable
private fun CircleCard(circle: Circle, people: Map<String, Person>, onInvite: () -> Unit, onOpenPerson: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(AppTheme.colors.surface).padding(AppSpace.m),
        verticalArrangement = Arrangement.spacedBy(AppSpace.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(circle.name, style = AppTheme.type.title, color = AppTheme.colors.ink)
                Text(
                    if (circle.kind == CircleKind.PAIR) stringResource(R.string.circle_pair) else pluralStringResource(R.plurals.members, circle.memberIds.size, circle.memberIds.size),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.inkMuted,
                )
            }
            val canInvite = circle.kind == CircleKind.GROUP || circle.memberIds.size < 2
            if (canInvite) {
                Row(
                    Modifier.clip(CircleShape).background(AppTheme.colors.surfaceMuted)
                        .pressable(onInvite).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(AppIcons.Share, null, tint = AppTheme.colors.ink, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.invite), style = AppTheme.type.callout, color = AppTheme.colors.ink)
                }
            }
        }
        Hairline(Modifier.padding(vertical = 4.dp))
        circle.memberIds.mapNotNull { people[it] }.forEach { person ->
            ListRow(onClick = { onOpenPerson(person.id) }, leading = { PersonAvatar(person, size = 36.dp) }, title = person.displayName)
        }
    }
}

@Composable
private fun CreateCircleSheet(onCreate: (String, CircleKind) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(CircleKind.PAIR) }
    Column(Modifier.padding(AppSpace.l).windowInsetsPadding(WindowInsets.navigationBars), verticalArrangement = Arrangement.spacedBy(AppSpace.m)) {
        Text(stringResource(R.string.create_circle), style = AppTheme.type.title, color = AppTheme.colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            ChoiceChip(stringResource(R.string.circle_pair), kind == CircleKind.PAIR, { kind = CircleKind.PAIR }, icon = AppIcons.Heart)
            ChoiceChip(stringResource(R.string.circle_group), kind == CircleKind.GROUP, { kind = CircleKind.GROUP }, icon = AppIcons.People)
        }
        AppTextField(name, { name = it.take(60) }, label = stringResource(R.string.circle_name), placeholder = stringResource(R.string.circle_name_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        PrimaryButton(stringResource(R.string.invite), { onCreate(name.trim(), kind) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank(), leadingIcon = AppIcons.Share)
    }
}

@Composable
fun JoinScreen(state: JoinUiState, onJoin: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(AppTheme.colors.canvas).windowInsetsPadding(WindowInsets.statusBars).padding(AppSpace.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpace.m, Alignment.CenterVertically),
    ) {
        GiftBoxMoment(Modifier.size(140.dp))
        val preview = state.preview
        when {
            state.loading -> Text(stringResource(R.string.fetching_link), style = AppTheme.type.body, color = AppTheme.colors.inkMuted)
            preview == null -> {
                Text(stringResource(R.string.error_generic), style = AppTheme.type.body, color = AppTheme.colors.inkMuted)
                SecondaryButton(stringResource(R.string.action_back), onClose)
            }
            else -> {
                Text(stringResource(R.string.join_title, preview.circleName), style = AppTheme.type.displayMedium, color = AppTheme.colors.ink)
                val members = if (preview.kind == CircleKind.PAIR) stringResource(R.string.circle_pair) else pluralStringResource(R.plurals.members, preview.memberCount, preview.memberCount)
                Text(stringResource(R.string.join_body, preview.inviterName, members), style = AppTheme.type.body, color = AppTheme.colors.inkMuted)
                PrimaryButton(stringResource(R.string.join), onJoin, Modifier.fillMaxWidth(), loading = state.joining)
                SecondaryButton(stringResource(R.string.action_cancel), onClose, Modifier.fillMaxWidth())
            }
        }
    }
}
