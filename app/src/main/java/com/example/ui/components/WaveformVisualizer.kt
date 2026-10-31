package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioSurfaceElevated

@Composable
fun WaveformVisualizer(
    waveformPoints: List<Float>,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
    } else 0f

    val safePoints = if (waveformPoints.isEmpty()) {
        List(64) { 0.3f }
    } else waveformPoints

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .testTag("waveform_visualizer")
            .pointerInput(totalDurationMs) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    val targetMs = (ratio * totalDurationMs).toLong()
                    onSeek(targetMs)
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f

            val barCount = safePoints.size
            val barSpacing = canvasWidth / barCount
            val barWidth = (barSpacing * 0.65f).coerceAtLeast(2f)

            val playheadX = progress * canvasWidth

            // Draw each amplitude bar
            for (i in 0 until barCount) {
                val point = safePoints[i].coerceIn(0.08f, 1.0f)
                val barX = i * barSpacing + (barSpacing - barWidth) / 2f
                val barHalfHeight = (point * (canvasHeight * 0.42f)).coerceAtLeast(4f)

                val isPlayed = barX <= playheadX

                val barColor = if (isPlayed) {
                    when {
                        point > 0.8f -> NeonPink
                        point > 0.6f -> NeonPurple
                        else -> NeonCyan
                    }
                } else {
                    StudioSurfaceElevated.copy(alpha = 0.85f)
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(barX, centerY - barHalfHeight),
                    size = Size(barWidth, barHalfHeight * 2f),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }

            // Draw subtle horizontal center line
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(0f, centerY),
                end = Offset(canvasWidth, centerY),
                strokeWidth = 1f
            )

            // Draw glowing playhead vertical scrubber
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(NeonCyan, Color.White, NeonPink)
                ),
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, canvasHeight),
                strokeWidth = 3f
            )

            // Playhead indicator diamond/circle
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(playheadX, centerY)
            )
        }
    }
}
