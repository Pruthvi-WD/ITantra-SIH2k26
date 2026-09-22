package com.example.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VoxCyanLight
import com.example.ui.theme.VoxCyanPrimary
import com.example.ui.theme.VoxEmeraldActive

/**
 * Real-time audio waveform visualizer driven by actual microphone PCM amplitude.
 */
@Composable
fun WaveformVisualizer(
    amplitudes: FloatArray,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = VoxCyanPrimary,
    glowColor: Color = VoxEmeraldActive
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val count = amplitudes.size
        if (count == 0) return@Canvas

        val barSpacing = 4.dp.toPx()
        val totalSpacing = barSpacing * (count - 1)
        val barWidth = (size.width - totalSpacing) / count
        val centerY = size.height / 2f

        val gradient = Brush.verticalGradient(
            colors = listOf(
                VoxCyanLight,
                activeColor,
                activeColor.copy(alpha = 0.7f)
            )
        )

        for (i in 0 until count) {
            val rawAmp = amplitudes[i]
            val heightFraction = if (isRecording) {
                rawAmp.coerceIn(0.12f, 1.0f) * (if (i % 2 == 0) pulseAlpha else 1f)
            } else {
                0.08f
            }
            val barHeight = maxOf(4.dp.toPx(), size.height * heightFraction)
            val left = i * (barWidth + barSpacing)
            val top = centerY - (barHeight / 2f)

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
