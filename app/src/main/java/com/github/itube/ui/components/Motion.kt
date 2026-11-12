package com.github.itube

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The single motion vocabulary (shared with Castii). Tuning dials:
 *  - [Emphasized] ease-out for page changes and small reveals.
 *  - [Morph] the slow-start, single-soft-overshoot curve for the player morph.
 */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
    val Morph = CubicBezierEasing(0.35f, 0f, 0.15f, 1.12f)
    /** Emphasized accelerate: things leaving (cleared queue cards sliding away). */
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** A spring-like pop with one overshoot, for things driven by a progress (no time of their own). */
    val Pop = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    const val PageEnterMs = 220
    const val PageRiseMs = 280
    const val PageExitMs = 90
    /** Tap-initiated open: slow start so the eye sees where the video grows from. */
    const val MorphMs = 480
    /** Tap/back-initiated collapse. Gesture releases use PlayerMorph's spring instead. */
    const val CollapseMs = 380
    /** Overlay controls hide after this long without a touch or remote interaction while playing. */
    const val ControlsHideMs = 4_000L
}

/** True when animations are off system-wide; all motion collapses to instant switches. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) == 0f
    }
}

/**
 * Tap and D-Pad focus target: handles touch press, remote D-pad focus highlight with
 * TV focus border, subtle zoom scaling, and click handling.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    focusedScale: Float = 1.05f,
    focusBorderRadius: Dp = 16.dp,
    focusShape: Shape? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed && !reduced -> pressedScale
            focused && !reduced -> focusedScale
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 1200f),
        label = "pressScale",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(120),
        label = "focusBorderAlpha",
    )
    val shapeToUse = focusShape ?: RoundedCornerShape(focusBorderRadius)
    val layer = this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            if (focused) shadowElevation = 16f
        }
        .then(
            if (borderAlpha > 0.01f) {
                Modifier.border(
                    width = 3.dp,
                    color = Color.White.copy(alpha = borderAlpha),
                    shape = shapeToUse,
                )
            } else Modifier
        )
    if (!enabled) return@composed layer
    layer
        .focusable(interactionSource = interaction, enabled = enabled)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { long -> { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); long() } },
            onClick = onClick,
        )
}
