package com.thinkofme.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.thinkofme.core.designsystem.component.CountdownRing
import com.thinkofme.core.designsystem.component.MediaTile
import com.thinkofme.core.designsystem.component.WrapStage
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.designsystem.theme.ThinkOfMeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the signature animations frame by frame (build/outputs/motion), so their
 * timing and shapes can be reviewed without a device. Not part of the store screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class MotionFramesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wrapMoment() {
        var wrapped by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ThinkOfMeTheme {
                Box(Modifier.background(AppTheme.colors.canvas).padding(horizontal = 24.dp, vertical = 70.dp)) {
                    WrapStage(wrapped, Modifier.fillMaxWidth().height(300.dp)) { MediaTile(Modifier.matchParentSize()) {} }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        wrapped = true
        var now = 0
        for (at in listOf(150, 300, 450, 600, 750, 900, 1000, 1100, 1250, 1450, 1700, 2400)) {
            compose.mainClock.advanceTimeBy((at - now).toLong()); now = at
            compose.onRoot().captureRoboImage("build/outputs/motion/wrap_%04d.png".format(at))
        }
    }

    @Test fun countdownRing() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ThinkOfMeTheme {
                Box(Modifier.background(AppTheme.colors.canvas).size(140.dp), contentAlignment = Alignment.Center) {
                    CountdownRing(days = 12, label = "days", accessibilityLabel = "12 days")
                }
            }
        }
        var now = 0
        for (at in listOf(100, 400, 700, 1000, 1300, 1800)) {
            compose.mainClock.advanceTimeBy((at - now).toLong()); now = at
            compose.onRoot().captureRoboImage("build/outputs/motion/ring_%04d.png".format(at))
        }
    }

    @Test fun scanner() {
        val bitmap = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(236, 228, 214))
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.rgb(46, 52, 64); drawRoundRect(250f, 140f, 550f, 470f, 40f, 40f, paint)
            paint.color = android.graphics.Color.rgb(214, 90, 70); drawCircle(400f, 260f, 70f, paint)
            paint.color = android.graphics.Color.rgb(250, 210, 80); drawRect(560f, 380f, 720f, 450f, paint)
        }
        val image = bitmap.asImageBitmap()
        var phase by mutableStateOf(0f)
        compose.setContent {
            ThinkOfMeTheme {
                Box(Modifier.background(AppTheme.colors.canvas).padding(24.dp)) {
                    com.thinkofme.feature.wishes.PhotoScanner(image, phase = phase)
                }
            }
        }
        for (step in 0 until 6) {
            phase = 0.06f + step * 0.16f
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/motion/scan_%02d.png".format(step))
        }
    }
}
