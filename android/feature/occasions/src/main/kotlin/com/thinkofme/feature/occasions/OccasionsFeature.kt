package com.thinkofme.feature.occasions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.thinkofme.core.data.AuthRepository
import com.thinkofme.core.data.CircleRepository
import com.thinkofme.core.data.ObserveOverview
import com.thinkofme.core.data.OccasionRepository
import com.thinkofme.core.data.ProfileRepository
import com.thinkofme.core.data.Today
import com.thinkofme.core.designsystem.component.AppTextField
import com.thinkofme.core.designsystem.component.ChoiceChip
import com.thinkofme.core.designsystem.component.EmptyState
import com.thinkofme.core.designsystem.component.Hairline
import com.thinkofme.core.designsystem.component.IconCircleButton
import com.thinkofme.core.designsystem.component.ListRow
import com.thinkofme.core.designsystem.component.PrimaryButton
import com.thinkofme.core.designsystem.component.SecondaryButton
import com.thinkofme.core.designsystem.icon.AppIcons
import com.thinkofme.core.designsystem.theme.AppSpace
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.model.ALL_CIRCLES
import com.thinkofme.core.model.Circle
import com.thinkofme.core.model.Holidays
import com.thinkofme.core.model.Occasion
import com.thinkofme.core.model.OccasionKind
import com.thinkofme.core.model.Person
import com.thinkofme.core.model.UpcomingOccasion
import com.thinkofme.core.ui.PersonAvatar
import com.thinkofme.core.ui.R
import com.thinkofme.core.ui.ads.InlineBanner
import com.thinkofme.core.ui.holidayName
import com.thinkofme.core.ui.mediumDate
import com.thinkofme.core.ui.occasionTitle
import com.thinkofme.core.ui.relativeDays
import com.thinkofme.core.ui.shortDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@Serializable data object OccasionsRoute

data class OccasionsUiState(
    val loading: Boolean = true,
    val upcoming: List<UpcomingOccasion> = emptyList(),
    val people: Map<String, Person> = emptyMap(),
    val circles: List<Circle> = emptyList(),
    val meId: String? = null,
    /** Holidays the user celebrates, with their next date, soonest first. */
    val holidays: List<Pair<com.thinkofme.core.model.Holiday, LocalDate>> = emptyList(),
)

@HiltViewModel
class OccasionsViewModel @Inject constructor(
    private val repo: OccasionRepository,
    profiles: ProfileRepository,
    circles: CircleRepository,
    auth: AuthRepository,
    today: Today,
) : ViewModel() {
    val state: StateFlow<OccasionsUiState> = combine(
        repo.occasions(), profiles.people(), circles.circles(), auth.session, profiles.holidayChoices(),
    ) { occasions, people, circleList, session, chosen ->
        val ids = chosen ?: Holidays.defaultsFor(java.util.Locale.getDefault().language)
        val holidays = Holidays.all.filter { it.id in ids }.mapNotNull { h -> h.nextDate(today())?.let { h to it } }.sortedBy { it.second }
        OccasionsUiState(false, ObserveOverview.upcoming(occasions, people, today()), people, circleList, session?.id, holidays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OccasionsUiState())

    fun save(occasion: Occasion) {
        viewModelScope.launch { runCatching { repo.save(occasion) } }
    }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { repo.delete(id) } }
    }
}

fun NavGraphBuilder.occasionsScreen(onOpenPerson: (String) -> Unit) {
    composable<OccasionsRoute> {
        val vm: OccasionsViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        OccasionsScreen(state, onOpenPerson, vm::save)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccasionsScreen(state: OccasionsUiState, onOpenPerson: (String) -> Unit, onSave: (Occasion) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(AppTheme.colors.canvas),
        contentPadding = PaddingValues(start = AppSpace.l, end = AppSpace.l, bottom = 120.dp),
    ) {
        item {
            Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSpace.l, bottom = AppSpace.m), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dates_title), style = AppTheme.type.displayLarge, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
                IconCircleButton(AppIcons.Plus, stringResource(R.string.add_date), { adding = true })
            }
        }
        if (!state.loading && state.upcoming.isEmpty()) {
            item { EmptyState(stringResource(R.string.dates_empty_title), stringResource(R.string.dates_empty_body), action = { PrimaryButton(stringResource(R.string.add_date), { adding = true }, leadingIcon = AppIcons.Plus) }) }
        }
        itemsIndexed(state.upcoming, key = { _, u -> u.occasion.id }) { index, upcoming ->
            ListRow(
                onClick = { upcoming.person?.id?.let(onOpenPerson) },
                leading = { PersonAvatar(upcoming.person, size = 44.dp) },
                title = occasionTitle(upcoming, state.meId),
                subtitle = { Text(shortDate(upcoming.date), style = AppTheme.type.caption, color = AppTheme.colors.inkMuted) },
                trailing = { DaysChip(upcoming.daysUntil) },
            )
            if (index < state.upcoming.lastIndex) Hairline()
        }
        if (state.holidays.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.settings_holidays),
                    style = AppTheme.type.title,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.padding(top = AppSpace.xl, bottom = AppSpace.xs),
                )
            }
            itemsIndexed(state.holidays, key = { _, h -> "holiday_${h.first.id}" }) { index, (holiday, date) ->
                ListRow(
                    onClick = {},
                    leading = {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colors.surfaceTint),
                            contentAlignment = Alignment.Center,
                        ) { androidx.compose.material3.Icon(AppIcons.Gift, null, tint = AppTheme.colors.accentText, modifier = Modifier.size(22.dp)) }
                    },
                    title = holidayName(holiday),
                    subtitle = { Text(shortDate(date), style = AppTheme.type.caption, color = AppTheme.colors.inkMuted) },
                    trailing = { DaysChip(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date)) },
                )
                if (index < state.holidays.lastIndex) Hairline()
            }
        }
        if (state.upcoming.size >= 6) item { InlineBanner(Modifier.padding(top = AppSpace.l)) }
    }

    if (adding) {
        ModalBottomSheet(onDismissRequest = { adding = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppTheme.colors.canvas) {
            AddOccasionSheet(state, onSave = { onSave(it); adding = false })
        }
    }
}

@Composable
private fun DaysChip(days: Long) {
    val soon = days <= 14
    Text(
        relativeDays(days),
        style = AppTheme.type.caption.copy(fontWeight = AppTheme.type.bodyStrong.fontWeight),
        color = if (soon) AppTheme.colors.accentText else AppTheme.colors.inkMuted,
        modifier = Modifier.clip(CircleShape).background(if (soon) AppTheme.colors.surfaceTint else AppTheme.colors.surfaceMuted).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddOccasionSheet(state: OccasionsUiState, onSave: (Occasion) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(OccasionKind.BIRTHDAY) }
    var personId by rememberSaveable { mutableStateOf(state.meId) }
    var title by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var yearly by rememberSaveable { mutableStateOf(true) }
    var circleIds by rememberSaveable { mutableStateOf(listOf(ALL_CIRCLES)) }
    var picking by remember { mutableStateOf(false) }
    val date = dateEpochDay?.let(LocalDate::ofEpochDay)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(AppSpace.l).windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.spacedBy(AppSpace.m),
    ) {
        Text(stringResource(R.string.add_date), style = AppTheme.type.title, color = AppTheme.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            listOf(
                OccasionKind.BIRTHDAY to R.string.kind_birthday_title, OccasionKind.ANNIVERSARY to R.string.kind_anniversary_title,
                OccasionKind.HOLIDAY to R.string.kind_holiday_title, OccasionKind.CUSTOM to R.string.kind_custom_title,
            ).forEach { (k, label) -> ChoiceChip(stringResource(label), kind == k, { kind = k }) }
        }
        Text(stringResource(R.string.date_whose), style = AppTheme.type.caption, color = AppTheme.colors.inkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            ChoiceChip(stringResource(R.string.me), personId == state.meId, { personId = state.meId })
            state.people.values.filter { it.id != state.meId }.sortedBy { it.displayName }.forEach { p ->
                ChoiceChip(p.displayName, personId == p.id, { personId = p.id })
            }
        }
        if (kind != OccasionKind.BIRTHDAY) {
            AppTextField(title, { title = it.take(80) }, stringResource(R.string.date_title), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        }
        SecondaryButton(date?.let { mediumDate(it) } ?: stringResource(R.string.date_pick), { picking = true }, Modifier.fillMaxWidth(), leadingIcon = AppIcons.Calendar)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.date_yearly), style = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Switch(yearly, { yearly = it }, colors = SwitchDefaults.colors(checkedTrackColor = AppTheme.colors.accent, checkedThumbColor = AppTheme.colors.onAccent))
        }
        Text(stringResource(R.string.visible_to), style = AppTheme.type.caption, color = AppTheme.colors.inkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            ChoiceChip(stringResource(R.string.visible_all), ALL_CIRCLES in circleIds, { circleIds = if (ALL_CIRCLES in circleIds) emptyList() else listOf(ALL_CIRCLES) })
            state.circles.forEach { c -> ChoiceChip(c.name, c.id in circleIds, { circleIds = if (c.id in circleIds) circleIds - c.id else circleIds - ALL_CIRCLES + c.id }) }
        }
        val birthdayTitle = stringResource(R.string.kind_birthday_title)
        PrimaryButton(
            stringResource(R.string.action_save),
            {
                val d = date ?: return@PrimaryButton
                onSave(
                    Occasion(
                        id = "", ownerId = state.meId.orEmpty(), personId = personId, kind = kind,
                        title = if (kind == OccasionKind.BIRTHDAY) birthdayTitle else title.trim(),
                        month = d.monthValue, day = d.dayOfMonth, year = d.year, yearly = yearly, visibleCircleIds = circleIds,
                    ),
                )
            },
            Modifier.fillMaxWidth(),
            enabled = date != null && (kind == OccasionKind.BIRTHDAY || title.isNotBlank()),
        )
    }

    if (picking) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    pickerState.selectedDateMillis?.let { dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    picking = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = { TextButton({ picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(pickerState) }
    }
}
