package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SongAnalysis
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioSurfaceCard
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MetricCardsGrid(
    analysis: SongAnalysis,
    isPlaying: Boolean,
    onOpenTapBpm: () -> Unit,
    onOpenCamelot: () -> Unit,
    onHalveBpm: () -> Unit = {},
    onDoubleBpm: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // BPM Pulse animation synced to tempo when playing
    val bpm = analysis.bpm.coerceAtLeast(40.0)
    val beatDurationMs = ((60.0 / bpm) * 1000).toInt()

    val infiniteTransition = rememberInfiniteTransition(label = "bpm_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = beatDurationMs / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Duration and BPM
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Duration Card
            MetricCard(
                title = "Duration",
                icon = Icons.Default.AvTimer,
                iconTint = NeonCyan,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_duration")
            ) {
                Text(
                    text = analysis.durationFormatted,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${analysis.durationMs / 1000}s • ${analysis.totalBars} Bars",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${analysis.fileFormat} • ${analysis.bitrateKbps} kbps",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            // BPM Card
            MetricCard(
                title = "Tempo (BPM)",
                icon = Icons.Default.Speed,
                iconTint = NeonPink,
                onClick = onOpenTapBpm,
                actionLabel = "Tap to Verify",
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_bpm")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "%.0f".format(analysis.bpm),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BPM",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonPink
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Pulsing beat metronome dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .scale(if (isPlaying) pulseScale else 1.0f)
                            .clip(CircleShape)
                            .background(if (isPlaying) NeonPink else TextMuted)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                val tempoFeel = when {
                    analysis.bpm < 80 -> "Largo / Slow"
                    analysis.bpm < 108 -> "Andante / Moderate"
                    analysis.bpm < 120 -> "Moderato / Groove"
                    analysis.bpm < 135 -> "Allegro / Fast"
                    else -> "Presto / High Energy"
                }
                Text(
                    text = tempoFeel,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioSurfaceElevated)
                            .clickable { onHalveBpm() }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "÷2",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioSurfaceElevated)
                            .clickable { onDoubleBpm() }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "×2",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenTapBpm() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = NeonPink,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Tap",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonPink,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Row 2: Musical Key and Genre
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Key Card
            MetricCard(
                title = "Musical Key",
                icon = Icons.Default.MusicNote,
                iconTint = NeonPurple,
                onClick = onOpenCamelot,
                actionLabel = "Camelot Wheel",
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_key")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = analysis.key,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Camelot Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonPurple.copy(alpha = 0.25f))
                            .border(1.dp, NeonPurple, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = analysis.camelotCode,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonPurple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Time Signature: ${analysis.timeSignature}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "View Harmonic Wheel",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonPurple
                )
            }

            // Genre Card
            MetricCard(
                title = "Character",
                icon = Icons.Default.Style,
                iconTint = NeonAmber,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_genre")
            ) {
                Text(
                    text = analysis.genre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = analysis.mood,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Beat clarity: ${(analysis.danceability * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonAmber
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = StudioSurfaceCard
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(iconTint.copy(alpha = 0.5f), StudioSurfaceElevated)
            ),
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            content()
        }
    }
}
