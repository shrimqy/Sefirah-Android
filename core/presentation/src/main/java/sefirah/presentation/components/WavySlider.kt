package sefirah.presentation.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A custom slider with a wavy effect that animates along the active track when playing.
 *
 * Original implementation based on WavyMusicSlider from PixelPlay:
 * https://github.com/theovilardo/PixelPlay
 * Licensed under MIT License
 *
 * @param value The current value of the slider (between 0f and 1f)
 * @param onValueChange Callback invoked when the value changes
 * @param modifier Modifier to apply to this composable
 * @param enabled Whether the slider is enabled
 * @param valueRange Range of allowed values
 * @param onValueChangeFinished Callback invoked when interaction with the slider ends
 * @param interactionSource Source of interaction for this slider
 * @param isPlaying Whether the associated content is currently playing (affects wave animation)
 * @param trackHeight Height of the slider track
 * @param thumbRadius Radius of the thumb when in circle form
 * @param thumbLineHeight Height of the vertical line when thumb is in interaction state
 * @param thumbGap Gap between thumb and track when interacting
 * @param activeTrackColor Color of the active portion of the track
 * @param inactiveTrackColor Color of the inactive portion of the track
 * @param thumbColor Color of the thumb
 * @param waveAmplitudeWhenPlaying Amplitude of the wave when playing
 * @param waveLength Length of the wave expressed in Dp. Controls the distance between wave peaks.
 * @param waveAnimationDuration Duration of the wave animation in milliseconds
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isPlaying: Boolean = false,
    trackHeight: Dp = 6.dp,
    thumbRadius: Dp = 9.dp,
    thumbLineHeight: Dp = 24.dp,
    thumbGap: Dp = 4.dp,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    waveAmplitudeWhenPlaying: Dp = 3.dp,
    waveLength: Dp = 40.dp,
    waveAnimationDuration: Int = 2000,
) {
    val isThumbPressed by interactionSource.collectIsPressedAsState()
    val isThumbDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isThumbPressed || isThumbDragged

    // Animate interaction fraction for smooth morphing
    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    // Wave animation - only show when playing and not interacting
    val shouldShowWave = isPlaying && !isInteracting

    val animatedWaveAmplitude by animateDpAsState(
        targetValue = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeAnim"
    )

    // Phase shift animation for wave movement
    val infiniteTransition = rememberInfiniteTransition(label = "Wave animation")
    val animationProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = waveAnimationDuration,
                easing = LinearEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "Wave progress"
    )
    // Convert progress to phase shift (0 to 2π)
    val phaseShift = animationProgress.value * (2 * PI).toFloat()

    val density = LocalDensity.current
    val trackHeightPx = with(density) { trackHeight.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeight.toPx() }
    val thumbGapPx = with(density) { thumbGap.toPx() }
    val waveAmplitudePx = with(density) { animatedWaveAmplitude.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx() }
    val waveFrequency = if (waveLengthPx > 0f) {
        ((2 * PI) / waveLengthPx).toFloat()
    } else {
        0f
    }

    // Calculate the gap that includes thumb radius when interacting
    val thumbGapWithRadius = thumbGapPx + thumbRadiusPx

    // Path for drawing wave
    val wavePath = remember { Path() }

    val sliderVisualHeight = remember(trackHeight, thumbRadius, thumbLineHeight) {
        max(trackHeight * 2, max(thumbRadius * 2, thumbLineHeight) + 8.dp)
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        // Transparent slider for gesture handling
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight),
            enabled = enabled,
            valueRange = valueRange,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )

        // Custom drawing for track and thumb
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .drawWithCache {
                    val canvasWidth = size.width
                    val localCenterY = size.height / 2f
                    val localTrackEnd = canvasWidth - thumbRadiusPx
                    val localTrackWidth = (localTrackEnd - thumbRadiusPx).coerceAtLeast(0f)

                    val normalizedValue = value.let { v ->
                        if (valueRange.endInclusive == valueRange.start) 0f
                        else ((v - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(
                            0f,
                            1f
                        )
                    }

                    onDrawWithContent {
                        val currentProgressPxEndVisual =
                            thumbRadiusPx + localTrackWidth * normalizedValue

                        // Draw inactive track - with gap from thumb when interacting
                        val inactiveTrackStart = currentProgressPxEndVisual + (thumbGapWithRadius * thumbInteractionFraction)
                        if (inactiveTrackStart < localTrackEnd) {
                            drawLine(
                                color = inactiveTrackColor,
                                start = Offset(inactiveTrackStart, localCenterY),
                                end = Offset(localTrackEnd, localCenterY),
                                strokeWidth = trackHeightPx,
                                cap = StrokeCap.Round
                            )
                        }

                        // Draw active track - always starts at the same position, gap only on the right side when interacting
                        if (normalizedValue > 0f) {
                            // Active track always starts at thumbRadiusPx (no gap on left)
                            // When not interacting: track extends to thumb center (no gap on right)
                            // When interacting: track stops before thumb with gap (gap on right only)
                            val activeTrackVisualEnd = currentProgressPxEndVisual - (thumbGapWithRadius * thumbInteractionFraction)

                            if (activeTrackVisualEnd > thumbRadiusPx) {
                                // Draw wave if amplitude is significant and frequency is valid
                                if (waveAmplitudePx > 0.01f && waveFrequency > 0f) {
                                    wavePath.reset()
                                    val waveEndDrawX = activeTrackVisualEnd.coerceAtLeast(
                                        thumbRadiusPx
                                    )

                                    if (waveEndDrawX > thumbRadiusPx) {
                                        val periodPx = ((2 * PI) / waveFrequency).toFloat()
                                        val samplesPerCycle = 20f
                                        val waveStep = (periodPx / samplesPerCycle)
                                            .coerceAtLeast(1.2f)
                                            .coerceAtMost(trackHeightPx)

                                        fun yAt(x: Float): Float {
                                            val s = sin(waveFrequency * x + phaseShift)
                                            return (localCenterY + waveAmplitudePx * s)
                                                .coerceIn(
                                                    localCenterY - waveAmplitudePx - trackHeightPx / 2f,
                                                    localCenterY + waveAmplitudePx + trackHeightPx / 2f
                                                )
                                        }

                                        var prevX = thumbRadiusPx
                                        var prevY = yAt(prevX)
                                        wavePath.moveTo(prevX, prevY)

                                        var x = prevX + waveStep
                                        while (x < waveEndDrawX) {
                                            val y = yAt(x)
                                            val midX = (prevX + x) * 0.5f
                                            val midY = (prevY + y) * 0.5f
                                            wavePath.quadraticTo(prevX, prevY, midX, midY)
                                            prevX = x
                                            prevY = y
                                            x += waveStep
                                        }
                                        val endY = yAt(waveEndDrawX)
                                        wavePath.quadraticTo(prevX, prevY, waveEndDrawX, endY)

                                        drawPath(
                                            path = wavePath,
                                            color = activeTrackColor,
                                            style = Stroke(
                                                width = trackHeightPx,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round,
                                                miter = 1f
                                            )
                                        )
                                    }
                                } else {
                                    // Draw straight line when wave is not active
                                    drawLine(
                                        color = activeTrackColor,
                                        start = Offset(thumbRadiusPx, localCenterY),
                                        end = Offset(activeTrackVisualEnd, localCenterY),
                                        strokeWidth = trackHeightPx,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        // Draw thumb - morphs from circle to vertical line
                        val currentThumbCenterX =
                            thumbRadiusPx + localTrackWidth * normalizedValue
                        val thumbCurrentWidthPx = lerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction)
                        val thumbCurrentHeightPx = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)

                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(
                                currentThumbCenterX - thumbCurrentWidthPx / 2f,
                                localCenterY - thumbCurrentHeightPx / 2f
                            ),
                            size = Size(thumbCurrentWidthPx, thumbCurrentHeightPx),
                            cornerRadius = CornerRadius(thumbCurrentWidthPx / 2f)
                        )
                    }
                }
        )
    }
}
