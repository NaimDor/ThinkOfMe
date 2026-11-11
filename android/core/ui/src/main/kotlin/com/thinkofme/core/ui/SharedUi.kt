package com.thinkofme.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import coil3.compose.AsyncImage
import com.thinkofme.core.designsystem.component.InitialAvatar
import com.thinkofme.core.designsystem.component.pressable
import com.thinkofme.core.designsystem.icon.AppIcons
import com.thinkofme.core.designsystem.theme.AppRadius
import com.thinkofme.core.designsystem.theme.AppSpace
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.model.AboutMe
import com.thinkofme.core.model.Money
import com.thinkofme.core.model.OccasionKind
import com.thinkofme.core.model.Person
import com.thinkofme.core.model.Priority
import com.thinkofme.core.model.UpcomingOccasion
import com.thinkofme.core.model.Wish
import com.thinkofme.core.model.WishKind
import java.text.NumberFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

@Composable
@ReadOnlyComposable
fun formatMoney(money: Money): String {
    val format = NumberFormat.getCurrencyInstance(currentLocale())
    runCatching { format.currency = Currency.getInstance(money.currency) }
    format.maximumFractionDigits = if (money.amount % 1.0 == 0.0) 0 else 2
    return format.format(money.amount)
}

@Composable
@ReadOnlyComposable
fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> stringResource(R.string.greeting_morning)
    in 12..17 -> stringResource(R.string.greeting_afternoon)
    else -> stringResource(R.string.greeting_evening)
}

@Composable
@ReadOnlyComposable
fun occasionKindName(kind: OccasionKind): String = stringResource(
    when (kind) {
        OccasionKind.BIRTHDAY -> R.string.kind_birthday
        OccasionKind.ANNIVERSARY -> R.string.kind_anniversary
        OccasionKind.HOLIDAY -> R.string.kind_holiday
        OccasionKind.CUSTOM -> R.string.kind_custom
    },
)

/** "Maya's birthday", or the custom title for other occasions. */
@Composable
@ReadOnlyComposable
fun occasionTitle(upcoming: UpcomingOccasion, meId: String?): String {
    val occasion = upcoming.occasion
    // Birthdays always read "<name>'s birthday" in the viewer's language;
    // other occasions keep the title their creator wrote.
    if (occasion.kind != OccasionKind.BIRTHDAY && occasion.title.isNotBlank()) return occasion.title
    if (meId != null && upcoming.person?.id == meId) return stringResource(R.string.my_birthday)
    val name = upcoming.person?.takeIf { it.id != meId }?.displayName ?: stringResource(R.string.me)
    return stringResource(R.string.occasion_possessive, name, occasionKindName(occasion.kind))
}

@Composable
@ReadOnlyComposable
fun longDate(date: java.time.LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale()))

@Composable
@ReadOnlyComposable
fun shortDate(date: java.time.LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", currentLocale()))

@Composable
@ReadOnlyComposable
fun mediumDate(date: java.time.LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()))

@Composable
@ReadOnlyComposable
fun relativeDays(days: Long): String = when (days) {
    0L -> stringResource(R.string.today)
    1L -> stringResource(R.string.tomorrow)
    else -> pluralStringResource(R.plurals.days_to_go, days.toInt(), days.toInt())
}

@Composable
@ReadOnlyComposable
fun holidayName(holiday: com.thinkofme.core.model.Holiday): String = holiday.name(currentLocale().language)

@Composable
@ReadOnlyComposable
fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.NICE -> R.string.priority_nice
        Priority.LOVE -> R.string.priority_love
        Priority.DREAM -> R.string.priority_dream
    },
)

@Composable
fun PersonAvatar(person: Person?, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val photo = person?.photoUrl
    if (photo != null) {
        AsyncImage(
            model = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape).background(AppTheme.colors.surfaceMuted),
        )
    } else {
        InitialAvatar(person?.initial ?: "?", person?.hueIndex ?: 0, modifier, size)
    }
}

/** A wish's photo, or a calm tinted tile with a line icon when there is none. */
@Composable
fun WishImage(wish: Wish, modifier: Modifier = Modifier, iconSize: Dp = 44.dp) {
    Box(
        modifier.clip(RoundedCornerShape(AppRadius.l)).background(AppTheme.colors.surfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (wish.kind == WishKind.IDEA) AppIcons.Idea else AppIcons.Gift,
            contentDescription = null,
            tint = AppTheme.colors.inkMuted.copy(alpha = 0.6f),
            modifier = Modifier.size(iconSize),
        )
        wish.imageUrls.firstOrNull()?.let { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Wish tile for grids. [wrapped] shows the gold "Wrapped" tag; callers must
 * only pass true when the viewer is NOT the wish's owner.
 */
@Composable
fun WishCard(
    wish: Wish,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wrapped: Boolean = false,
    subtitle: String? = null,
) {
    Column(modifier.pressable(onClick), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
        Box {
            WishImage(wish, Modifier.fillMaxWidth().aspectRatio(1f))
            Row(Modifier.padding(AppSpace.xs), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (wrapped) Tag(stringResource(R.string.wrapped), gold = true)
                if (wish.kind == WishKind.IDEA) Tag(stringResource(R.string.idea))
            }
        }
        Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(wish.title, style = AppTheme.type.callout.copy(fontWeight = AppTheme.type.bodyStrong.fontWeight), color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = subtitle ?: wish.price?.let { formatMoney(it) } ?: priorityLabel(wish.priority)
            Text(meta, style = AppTheme.type.caption, color = AppTheme.colors.inkMuted, maxLines = 1)
        }
    }
}

@Composable
fun Tag(text: String, gold: Boolean = false) {
    Text(
        text,
        style = AppTheme.type.tab.copy(fontWeight = AppTheme.type.overline.fontWeight),
        color = if (gold) AppTheme.colors.onRibbon else AppTheme.colors.ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (gold) AppTheme.colors.ribbon else AppTheme.colors.surface.copy(alpha = 0.9f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Sizes and preferences, shown on a person's page. Hidden when empty. */
@Composable
fun AboutMeCard(about: AboutMe, modifier: Modifier = Modifier) {
    if (about.isEmpty) return
    val rows = listOfNotNull(
        about.clothingSize?.let { R.string.about_size_clothing to it },
        about.shoeSize?.let { R.string.about_size_shoes to it },
        about.ringSize?.let { R.string.about_size_ring to it },
        about.favoriteColors?.let { R.string.about_colors to it },
        about.allergies?.let { R.string.about_allergies to it },
        about.dontBuy?.let { R.string.about_dont_buy to it },
    ).filter { it.second.isNotBlank() }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.l))
            .background(AppTheme.colors.surface)
            .padding(AppSpace.m),
        verticalArrangement = Arrangement.spacedBy(AppSpace.xs),
    ) {
        Text(stringResource(R.string.about_me), style = AppTheme.type.overline, color = AppTheme.colors.accentText)
        rows.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.s)) {
                Text(stringResource(label), style = AppTheme.type.callout, color = AppTheme.colors.inkMuted, modifier = Modifier.weight(0.42f))
                Text(value, style = AppTheme.type.callout, color = AppTheme.colors.ink, modifier = Modifier.weight(0.58f))
            }
        }
    }
}
