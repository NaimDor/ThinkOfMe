package com.thinkofme.feature.wishes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.thinkofme.core.designsystem.component.AppTextField
import com.thinkofme.core.designsystem.component.ChoiceChip
import com.thinkofme.core.designsystem.component.IconCircleButton
import com.thinkofme.core.designsystem.component.PrimaryButton
import com.thinkofme.core.designsystem.component.pressable
import com.thinkofme.core.designsystem.component.TextAction
import com.thinkofme.core.designsystem.icon.AppIcons
import com.thinkofme.core.designsystem.theme.AppRadius
import com.thinkofme.core.designsystem.theme.AppSpace
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.model.ALL_CIRCLES
import com.thinkofme.core.model.CircleKind
import com.thinkofme.core.model.Priority
import com.thinkofme.core.model.WishKind
import com.thinkofme.core.ui.R
import com.thinkofme.core.ui.priorityLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditWishScreen(state: EditWishUiState, vm: EditWishViewModel, onClose: () -> Unit) {
    val draft = state.draft
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(AppTheme.colors.canvas)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(
                title = stringResource(if (state.isNew) R.string.new_wish else R.string.edit_wish),
                onBack = null,
                actions = { IconCircleButton(AppIcons.Close, stringResource(R.string.action_cancel), onClose) },
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = AppSpace.l, vertical = AppSpace.s),
                verticalArrangement = Arrangement.spacedBy(AppSpace.l),
            ) {
                AppTextField(
                    value = draft.url,
                    onValueChange = { vm.update { copy(url = it) } },
                    label = stringResource(R.string.field_link),
                    placeholder = stringResource(R.string.field_link_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.unfurl() }),
                    trailing = {
                        if (state.unfurling) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppTheme.colors.accent)
                        } else if (draft.url.startsWith("http")) {
                            Icon(AppIcons.Link, stringResource(R.string.fetching_link), tint = AppTheme.colors.accentText, modifier = Modifier.size(22.dp).pressable({ vm.unfurl() }))
                        }
                    },
                )
                AnimatedVisibility(state.unfurling || state.unfurlFailed) {
                    Text(
                        stringResource(if (state.unfurling) R.string.fetching_link else R.string.link_failed),
                        style = AppTheme.type.caption,
                        color = AppTheme.colors.inkMuted,
                    )
                }
                PhotoRow(
                    urls = draft.imageUrls,
                    uploading = state.uploadingPhoto,
                    onPicked = { uri -> encodePhoto(context, uri)?.let(vm::addPhoto) },
                    onRemove = vm::removePhoto,
                )
                PhotoReadingStatus(state, onFindOnline = { query ->
                    val uri = android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(query))
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                })

                Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
                    ChoiceChip(stringResource(R.string.field_kind_product), draft.kind == WishKind.PRODUCT, { vm.update { copy(kind = WishKind.PRODUCT) } }, icon = AppIcons.Gift)
                    ChoiceChip(stringResource(R.string.field_kind_idea), draft.kind == WishKind.IDEA, { vm.update { copy(kind = WishKind.IDEA) } }, icon = AppIcons.Idea)
                }

                AppTextField(
                    value = draft.title,
                    onValueChange = { if (it.length <= 140) vm.update { copy(title = it) } },
                    label = stringResource(R.string.field_title),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                if (draft.kind == WishKind.PRODUCT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.s)) {
                        AppTextField(
                            value = draft.priceText,
                            onValueChange = { text -> vm.update { copy(priceText = text.filter { it.isDigit() || it == '.' || it == ',' }.take(10)) } },
                            label = stringResource(R.string.field_price),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            trailing = { Text(draft.currency, style = AppTheme.type.callout, color = AppTheme.colors.inkMuted) },
                        )
                        AppTextField(
                            value = draft.store,
                            onValueChange = { vm.update { copy(store = it.take(120)) } },
                            label = stringResource(R.string.field_store),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                AppTextField(
                    value = draft.note,
                    onValueChange = { vm.update { copy(note = it.take(1000)) } },
                    label = stringResource(R.string.field_note),
                    placeholder = stringResource(R.string.field_note_hint),
                    singleLine = false,
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                Section(stringResource(R.string.how_much)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
                        Priority.entries.forEach { p ->
                            ChoiceChip(priorityLabel(p), draft.priority == p, { vm.update { copy(priority = p) } })
                        }
                    }
                }

                Section(stringResource(R.string.visible_to)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
                        ChoiceChip(stringResource(R.string.visible_all), ALL_CIRCLES in draft.visibleCircleIds, { vm.toggleCircle(ALL_CIRCLES) }, icon = AppIcons.People)
                        state.circles.forEach { circle ->
                            ChoiceChip(
                                circle.name,
                                circle.id in draft.visibleCircleIds,
                                { vm.toggleCircle(circle.id) },
                                icon = if (circle.kind == CircleKind.PAIR) AppIcons.Heart else AppIcons.People,
                            )
                        }
                    }
                    if (draft.visibleCircleIds.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(AppIcons.Lock, null, tint = AppTheme.colors.inkMuted, modifier = Modifier.size(14.dp))
                            Text(stringResource(R.string.visible_to_none), style = AppTheme.type.caption, color = AppTheme.colors.inkMuted)
                        }
                    }
                }
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = AppSpace.l, vertical = AppSpace.m)) {
                if (state.error) Text(stringResource(R.string.error_generic), style = AppTheme.type.caption, color = AppTheme.colors.danger, modifier = Modifier.padding(bottom = AppSpace.xs))
                PrimaryButton(stringResource(R.string.action_save), vm::save, Modifier.fillMaxWidth(), enabled = draft.isValid, loading = state.saving)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
        Text(title, style = AppTheme.type.caption.copy(fontWeight = AppTheme.type.bodyStrong.fontWeight), color = AppTheme.colors.inkMuted)
        content()
    }
}

/** Photos of the wish: an "add" tile (photo picker) followed by the current images. */
/** "Reading the photo…" while it works, then what it filled in and a way to find the product online. */
@Composable
private fun PhotoReadingStatus(state: EditWishUiState, onFindOnline: (String) -> Unit) {
    AnimatedVisibility(state.readingPhoto || state.filledFromPhoto || state.photoSearchQuery != null) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.readingPhoto) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppTheme.colors.accent)
                } else {
                    Icon(AppIcons.Sparkle, null, tint = AppTheme.colors.accentText, modifier = Modifier.size(16.dp))
                }
                Text(
                    stringResource(if (state.readingPhoto) R.string.reading_photo else R.string.filled_from_photo),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.inkMuted,
                )
            }
            val query = state.photoSearchQuery
            if (!state.readingPhoto && query != null) {
                TextAction(stringResource(R.string.find_online), { onFindOnline(query) })
            }
        }
    }
}

@Composable
private fun PhotoRow(urls: List<String>, uploading: Boolean, onPicked: (android.net.Uri) -> Unit, onRemove: (String) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
        if (urls.size < 8) {
            item(key = "add") {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(AppRadius.m)).background(AppTheme.colors.surfaceMuted)
                        .pressable(
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !uploading,
                            onClickLabel = stringResource(R.string.add_photo),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (uploading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = AppTheme.colors.accent)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(AppIcons.Photo, null, tint = AppTheme.colors.ink, modifier = Modifier.size(24.dp))
                            Text(stringResource(R.string.add_photo), style = AppTheme.type.tab, color = AppTheme.colors.ink)
                        }
                    }
                }
            }
        }
        items(urls, key = { it }) { url ->
            Box {
                AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(AppRadius.m)).background(AppTheme.colors.surfaceMuted))
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(AppTheme.colors.canvas.copy(alpha = 0.85f)).pressable(onClick = { onRemove(url) }, onClickLabel = stringResource(R.string.action_delete)),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Close, stringResource(R.string.action_delete), tint = AppTheme.colors.ink, modifier = Modifier.size(14.dp)) }
            }
        }
    }
}
