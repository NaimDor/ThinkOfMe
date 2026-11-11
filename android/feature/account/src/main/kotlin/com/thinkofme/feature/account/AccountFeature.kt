package com.thinkofme.feature.account

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.thinkofme.core.data.AuthRepository
import com.thinkofme.core.data.OccasionRepository
import com.thinkofme.core.data.ProfileRepository
import com.thinkofme.core.designsystem.component.AppTextField
import com.thinkofme.core.designsystem.component.GiftBoxMoment
import com.thinkofme.core.designsystem.component.Hairline
import com.thinkofme.core.designsystem.component.PrimaryButton
import com.thinkofme.core.designsystem.component.SecondaryButton
import com.thinkofme.core.designsystem.component.TextAction
import com.thinkofme.core.designsystem.icon.AppIcons
import com.thinkofme.core.designsystem.theme.AppSpace
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.model.ALL_CIRCLES
import com.thinkofme.core.model.AboutMe
import com.thinkofme.core.model.Occasion
import com.thinkofme.core.model.OccasionKind
import com.thinkofme.core.ui.R
import com.thinkofme.core.ui.mediumDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@Serializable data object SettingsRoute

// ---------- sign in ----------

data class SignInUiState(val busy: Boolean = false, val error: Boolean = false, val isDemo: Boolean = false)

@HiltViewModel
class SignInViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState(isDemo = auth.isDemo))
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun signInWithGoogle(context: Context, webClientId: String?) {
        if (auth.isDemo || webClientId.isNullOrBlank()) return demo()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = false) }
            val result = runCatching {
                val option = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = CredentialManager.create(context).getCredential(context, request).credential
                check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                auth.signInWithGoogleIdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            }
            val cancelled = result.exceptionOrNull() is GetCredentialCancellationException
            _state.update { it.copy(busy = false, error = result.isFailure && !cancelled) }
        }
    }

    fun demo() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { auth.signInWithDemoData() }
            _state.update { it.copy(busy = false) }
        }
    }
}

@Composable
fun SignInScreen(webClientId: String?) {
    val vm: SignInViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(AppTheme.colors.canvas).windowInsetsPadding(WindowInsets.systemBars).padding(AppSpace.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        GiftBoxMoment(Modifier.size(180.dp))
        Spacer(Modifier.height(AppSpace.l))
        Text(stringResource(R.string.welcome_title), style = AppTheme.type.displayLarge, color = AppTheme.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(AppSpace.s))
        Text(stringResource(R.string.welcome_body), style = AppTheme.type.body, color = AppTheme.colors.inkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        if (state.error) Text(stringResource(R.string.error_generic), style = AppTheme.type.caption, color = AppTheme.colors.danger, modifier = Modifier.padding(bottom = AppSpace.s))
        if (state.isDemo) {
            PrimaryButton(stringResource(R.string.explore_demo), vm::demo, Modifier.fillMaxWidth(), loading = state.busy, leadingIcon = AppIcons.Gift)
        } else {
            PrimaryButton(stringResource(R.string.continue_google), { vm.signInWithGoogle(context, webClientId) }, Modifier.fillMaxWidth(), loading = state.busy)
        }
    }
}

// ---------- onboarding ----------

data class OnboardingUiState(val name: String = "", val birthday: LocalDate? = null, val saving: Boolean = false, val error: Boolean = false)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
    private val occasions: OccasionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState(name = auth.session.value?.displayName.orEmpty()))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setName(name: String) = _state.update { it.copy(name = name.take(60)) }
    fun setBirthday(date: LocalDate) = _state.update { it.copy(birthday = date) }

    fun finish() {
        val s = _state.value
        if (s.name.isBlank() || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = false) }
            val ok = runCatching {
                profiles.saveProfile(s.name.trim(), AboutMe())
                s.birthday?.let { b ->
                    val me = auth.session.value?.id.orEmpty()
                    occasions.save(Occasion("", me, me, OccasionKind.BIRTHDAY, "Birthday", b.monthValue, b.dayOfMonth, b.year, true, listOf(ALL_CIRCLES)))
                }
            }.isSuccess
            _state.update { it.copy(saving = false, error = !ok) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen() {
    val vm: OnboardingViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(AppTheme.colors.canvas).windowInsetsPadding(WindowInsets.systemBars).imePadding().padding(AppSpace.l),
        verticalArrangement = Arrangement.spacedBy(AppSpace.m),
    ) {
        Spacer(Modifier.height(AppSpace.xl))
        Text(stringResource(R.string.onboarding_name), style = AppTheme.type.displayMedium, color = AppTheme.colors.ink)
        AppTextField(state.name, vm::setName, stringResource(R.string.settings_name), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        Spacer(Modifier.height(AppSpace.s))
        Text(stringResource(R.string.onboarding_birthday), style = AppTheme.type.displayMedium, color = AppTheme.colors.ink)
        Text(stringResource(R.string.onboarding_birthday_hint), style = AppTheme.type.callout, color = AppTheme.colors.inkMuted)
        SecondaryButton(state.birthday?.let { mediumDate(it) } ?: stringResource(R.string.date_pick), { picking = true }, Modifier.fillMaxWidth(), leadingIcon = AppIcons.Calendar)
        Spacer(Modifier.weight(1f))
        if (state.error) Text(stringResource(R.string.error_generic), style = AppTheme.type.caption, color = AppTheme.colors.danger)
        PrimaryButton(stringResource(R.string.onboarding_continue), vm::finish, Modifier.fillMaxWidth(), enabled = state.name.isNotBlank(), loading = state.saving)
    }
    if (picking) {
        val picker = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    picker.selectedDateMillis?.let { vm.setBirthday(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = { TextButton({ picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(picker) }
    }
}

// ---------- settings ----------

data class SettingsUiState(
    val name: String = "",
    val about: AboutMe = AboutMe(),
    val reminders: Boolean = true,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (me, reminders) = combine(profiles.me(), profiles.remindersEnabled()) { a, b -> a to b }.first()
            _state.update { it.copy(name = me?.displayName.orEmpty(), about = me?.aboutMe ?: AboutMe(), reminders = reminders, loaded = true) }
        }
    }

    fun edit(change: SettingsUiState.() -> SettingsUiState) = _state.update { it.change().copy(saved = false) }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching { profiles.saveProfile(s.name.trim(), s.about) }
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    fun setReminders(enabled: Boolean) {
        _state.update { it.copy(reminders = enabled) }
        viewModelScope.launch { runCatching { profiles.setRemindersEnabled(enabled) } }
    }

    fun signOut() = viewModelScope.launch { auth.signOut() }
    fun deleteAccount() = viewModelScope.launch { runCatching { auth.deleteAccount() } }
}

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onPrivacyOptions: (() -> Unit)?) {
    composable<SettingsRoute> { SettingsScreen(onBack, onPrivacyOptions) }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onPrivacyOptions: (() -> Unit)?) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(AppTheme.colors.canvas).imePadding()) {
        Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = AppSpace.l, vertical = AppSpace.xs), verticalAlignment = Alignment.CenterVertically) {
            com.thinkofme.core.designsystem.component.IconCircleButton(AppIcons.Back, stringResource(R.string.action_back), onBack)
            Text(stringResource(R.string.settings_title), style = AppTheme.type.headline, color = AppTheme.colors.ink, modifier = Modifier.padding(horizontal = AppSpace.s))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = AppSpace.l).windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(AppSpace.m),
        ) {
            Text(stringResource(R.string.settings_profile), style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.padding(top = AppSpace.s))
            AppTextField(state.name, { v -> vm.edit { copy(name = v.take(60)) } }, stringResource(R.string.settings_name))
            Text(stringResource(R.string.about_me), style = AppTheme.type.overline, color = AppTheme.colors.accentText)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.s)) {
                AppTextField(state.about.clothingSize.orEmpty(), { v -> vm.edit { copy(about = about.copy(clothingSize = v.take(20))) } }, stringResource(R.string.about_size_clothing), Modifier.weight(1f))
                AppTextField(state.about.shoeSize.orEmpty(), { v -> vm.edit { copy(about = about.copy(shoeSize = v.take(20))) } }, stringResource(R.string.about_size_shoes), Modifier.weight(1f))
                AppTextField(state.about.ringSize.orEmpty(), { v -> vm.edit { copy(about = about.copy(ringSize = v.take(20))) } }, stringResource(R.string.about_size_ring), Modifier.weight(1f))
            }
            AppTextField(state.about.favoriteColors.orEmpty(), { v -> vm.edit { copy(about = about.copy(favoriteColors = v.take(120))) } }, stringResource(R.string.about_colors))
            AppTextField(state.about.allergies.orEmpty(), { v -> vm.edit { copy(about = about.copy(allergies = v.take(120))) } }, stringResource(R.string.about_allergies))
            AppTextField(state.about.dontBuy.orEmpty(), { v -> vm.edit { copy(about = about.copy(dontBuy = v.take(200))) } }, stringResource(R.string.about_dont_buy), singleLine = false, minLines = 2)
            PrimaryButton(if (state.saved) stringResource(R.string.action_done) else stringResource(R.string.action_save), vm::save, Modifier.fillMaxWidth(), loading = state.saving, enabled = state.name.isNotBlank())

            Hairline(Modifier.padding(vertical = AppSpace.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_notifications), style = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
                    Text(stringResource(R.string.settings_notifications_body), style = AppTheme.type.caption, color = AppTheme.colors.inkMuted)
                }
                Switch(state.reminders, vm::setReminders, colors = SwitchDefaults.colors(checkedTrackColor = AppTheme.colors.accent, checkedThumbColor = AppTheme.colors.onAccent))
            }
            Hairline(Modifier.padding(vertical = AppSpace.xs))
            if (onPrivacyOptions != null) TextAction(stringResource(R.string.settings_privacy_ads), onPrivacyOptions)
            TextAction(stringResource(R.string.settings_sign_out), { vm.signOut() })
            TextAction(stringResource(R.string.settings_delete_account), { confirmDelete = true })
            Spacer(Modifier.height(AppSpace.xl))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_account)) },
            text = { Text(stringResource(R.string.delete_account_body)) },
            confirmButton = { TextButton({ confirmDelete = false; scope.launch { vm.deleteAccount() } }) { Text(stringResource(R.string.action_delete), color = AppTheme.colors.danger) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = AppTheme.colors.surface,
        )
    }
}
