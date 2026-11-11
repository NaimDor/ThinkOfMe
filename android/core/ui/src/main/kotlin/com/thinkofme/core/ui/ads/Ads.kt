package com.thinkofme.core.ui.ads

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.thinkofme.core.designsystem.theme.AppColors
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.ui.R

/**
 * Ads configuration supplied by the app. Placements and frequency are kept
 * conservative on purpose: native cards styled like our own content and
 * inline banners at the end of lists. Never full screen.
 */
@Immutable
data class AdsConfig(
    val enabled: Boolean = false,
    val nativeUnitId: String = "",
    val bannerUnitId: String = "",
    /** First native ad after this many items, then every [nativeEvery] items. */
    val nativeFirstAfter: Int = 6,
    val nativeEvery: Int = 10,
)

val LocalAdsConfig = staticCompositionLocalOf { AdsConfig() }

/** Whether a native ad belongs after the item at [index] in a feed. */
fun AdsConfig.nativeAdAfter(index: Int): Boolean {
    if (!enabled) return false
    val position = index + 1
    return position == nativeFirstAfter || (position > nativeFirstAfter && (position - nativeFirstAfter) % nativeEvery == 0)
}

/**
 * A native ad drawn in our own card style with a clear "Ad" label. If no ad
 * loads, nothing is shown (no empty box).
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val config = LocalAdsConfig.current
    if (!config.enabled || config.nativeUnitId.isBlank()) return
    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(config.nativeUnitId) {
        val loader = AdLoader.Builder(context, config.nativeUnitId)
            .forNativeAd { loaded -> ad = loaded }
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose { ad?.destroy() }
    }

    val colors = AppTheme.colors
    val label = stringResource(R.string.ad_label)
    AnimatedVisibility(visible = ad != null, enter = fadeIn() + expandVertically(), modifier = modifier) {
        val current = ad ?: return@AnimatedVisibility
        AndroidView(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            factory = { ctx -> buildNativeAdView(ctx, colors, label) },
            update = { view -> bindNativeAd(view, current) },
        )
    }
}

private fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

@SuppressLint("SetTextI18n")
private fun buildNativeAdView(context: Context, colors: AppColors, adLabel: String): NativeAdView {
    val adView = NativeAdView(context)
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
        background = GradientDrawable().apply {
            cornerRadius = context.dp(20).toFloat()
            setColor(colors.surface.toArgb())
        }
        layoutDirection = ViewGroup.LAYOUT_DIRECTION_LOCALE
    }
    val icon = ImageView(context).apply {
        id = android.R.id.icon
        layoutParams = LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply { marginEnd = context.dp(12) }
        clipToOutline = true
        background = GradientDrawable().apply { cornerRadius = context.dp(12).toFloat(); setColor(colors.surfaceMuted.toArgb()) }
    }
    val texts = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    val headline = TextView(context).apply {
        id = android.R.id.title
        setTextColor(colors.ink.toArgb()); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; maxLines = 1
    }
    val body = TextView(context).apply {
        id = android.R.id.summary
        setTextColor(colors.inkMuted.toArgb()); textSize = 13f; maxLines = 2
    }
    val label = TextView(context).apply {
        text = adLabel
        setTextColor(colors.onRibbon.toArgb()); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        setPadding(context.dp(7), context.dp(2), context.dp(7), context.dp(2))
        background = GradientDrawable().apply { cornerRadius = context.dp(8).toFloat(); setColor(colors.ribbon.toArgb()) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = context.dp(4) }
    }
    val cta = TextView(context).apply {
        id = android.R.id.button1
        setTextColor(colors.onAccent.toArgb()); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setPadding(context.dp(14), context.dp(8), context.dp(14), context.dp(8))
        background = GradientDrawable().apply { cornerRadius = context.dp(999).toFloat(); setColor(colors.accent.toArgb()) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(10) }
    }
    texts.addView(label); texts.addView(headline); texts.addView(body)
    row.addView(icon); row.addView(texts); row.addView(cta)
    adView.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    adView.iconView = icon
    adView.headlineView = headline
    adView.bodyView = body
    adView.callToActionView = cta
    return adView
}

private fun bindNativeAd(view: NativeAdView, ad: NativeAd) {
    (view.headlineView as TextView).text = ad.headline
    (view.bodyView as TextView).apply { text = ad.body; visibility = if (ad.body == null) android.view.View.GONE else android.view.View.VISIBLE }
    (view.callToActionView as TextView).apply { text = ad.callToAction; visibility = if (ad.callToAction == null) android.view.View.GONE else android.view.View.VISIBLE }
    (view.iconView as ImageView).apply {
        setImageDrawable(ad.icon?.drawable)
        visibility = if (ad.icon == null) android.view.View.GONE else android.view.View.VISIBLE
    }
    view.setNativeAd(ad)
}

/** A thin adaptive banner placed at the end of a long list. Collapses when no ad loads. */
@Composable
fun InlineBanner(modifier: Modifier = Modifier) {
    val config = LocalAdsConfig.current
    if (!config.enabled || config.bannerUnitId.isBlank()) return
    val widthDp = LocalConfiguration.current.screenWidthDp - 48
    var loaded by remember { mutableStateOf(false) }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                adUnitId = config.bannerUnitId
                setAdSize(AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(ctx, widthDp))
                adListener = object : AdListener() {
                    override fun onAdLoaded() { loaded = true }
                    override fun onAdFailedToLoad(error: LoadAdError) { loaded = false }
                }
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
        update = { it.visibility = if (loaded) android.view.View.VISIBLE else android.view.View.GONE },
    )
}
