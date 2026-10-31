package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SongSection
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioSurfaceCard
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongStructureTimeline(
    sections: List<SongSection>,
    totalDurationMs: Long,
    currentPositionMs: Long,
    loopingSection: SongSection?,
    onSectionClick: (SongSection) -> Unit,
    onToggleLoop: (SongSection) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sections.isEmpty() || totalDurationMs <= 0) return

    val currentSection = sections.firstOrNull {
        currentPositionMs >= it.startMs && currentPositionMs < it.endMs
    } ?: sections.lastOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Song Structure Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (currentSection != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentSection.type.toComposeColor().copy(alpha = 0.2f))
                        .border(
                            1.dp,
                            currentSection.type.toComposeColor().copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Now: ${currentSection.name}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = currentSection.type.toComposeColor()
                    )
                }
            }
        }

        // 1. Proportional Segmented Timeline Ribbon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StudioSurfaceElevated)
                .pointerInput(totalDurationMs, sections) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        val tapMs = (ratio * totalDurationMs).toLong()
                        val clicked = sections.firstOrNull { tapMs >= it.startMs && tapMs <= it.endMs }
                        if (clicked != null) {
                            onSectionClick(clicked)
                        }
                    }
                }
                .testTag("structure_timeline_ribbon")
        ) {
            // Draw section blocks
            Row(modifier = Modifier.matchParentSize()) {
                sections.forEach { section ->
                    val weight = (section.durationMs.toFloat() / totalDurationMs).coerceAtLeast(0.02f)
                    val isCurrent = currentSection == section
                    val isLooping = loopingSection == section

                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .height(38.dp)
                            .background(
                                section.type.toComposeColor().copy(
                                    alpha = if (isCurrent) 0.85f else 0.40f
                                )
                            )
                            .border(
                                width = if (isLooping) 2.dp else if (isCurrent) 1.5.dp else 0.5.dp,
                                color = if (isLooping) NeonAmber else if (isCurrent) Color.White else Color.Black.copy(alpha = 0.3f)
                            )
                            .clickable { onSectionClick(section) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = section.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            maxLines = 1,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            // Real-time moving playhead line on ribbon
            Canvas(modifier = Modifier.matchParentSize()) {
                val progress = (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                val playheadX = progress * size.width
                drawLine(
                    color = Color.White,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 3f
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Detailed Section Breakdown Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            sections.forEach { section ->
                val isCurrent = currentSection == section
                val isLooping = loopingSection == section

                val cardBorderColor by animateColorAsState(
                    targetValue = when {
                        isLooping -> NeonAmber
                        isCurrent -> section.type.toComposeColor()
                        else -> StudioSurfaceElevated
                    },
                    label = "border_color"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("section_card_${section.name.replace(" ", "_").lowercase()}")
                        .clickable { onSectionClick(section) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) StudioSurfaceCard else StudioSurfaceCard.copy(alpha = 0.65f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(cardBorderColor, cardBorderColor.copy(alpha = 0.4f))),
                        width = if (isCurrent || isLooping) 1.5.dp else 1.dp
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Section Color Indicator Dot
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(section.type.toComposeColor())
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Info Column
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = section.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(StudioSurfaceElevated)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = section.formatRange(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (isLooping) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(NeonAmber.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "LOOP ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = section.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Energy Meter
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Energy ${(section.energy * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )

                                LinearProgressIndicator(
                                    progress = { section.energy },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = section.type.toComposeColor(),
                                    trackColor = StudioSurfaceElevated
                                )
                            }

                            // Chords Tag
                            if (section.chordProgression.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Chords: ${section.chordProgression}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonPurple,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Action Buttons: Jump and Loop
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onToggleLoop(section) },
                                modifier = Modifier.testTag("loop_button_${section.name}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Loop,
                                    contentDescription = "Loop Section",
                                    tint = if (isLooping) NeonAmber else TextMuted
                                )
                            }

                            IconButton(
                                onClick = { onSectionClick(section) },
                                modifier = Modifier.testTag("jump_button_${section.name}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Jump to Section",
                                    tint = if (isCurrent) NeonCyan else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
