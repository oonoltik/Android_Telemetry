package com.alex.android_telemetry.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object TelemetrySwiftColors {
    val ScreenBackground = Color(0xFFF2F2F2)
    val CardBackground = Color(0xFFFFFFFF)
    val SecondaryCardBackground = Color(0xFFF7F7F7)
    val Accent = Color(0xFFFA8C1A)
    val TextPrimary = Color(0xFF111111)
    val TextSecondary = Color(0xFF6E6E73)
    val Divider = Color(0xFFE5E5EA)

    val ScoreGreen = Color(0xFF34C759)
    val ScoreYellow = Color(0xFFFFCC00)
    val ScoreRed = Color(0xFFFF3B30)
    val ScoreGray = Color(0xFF8E8E93)
}

@Composable
fun SwiftScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(TelemetrySwiftColors.ScreenBackground)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}


@Composable
fun SwiftCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = TelemetrySwiftColors.CardBackground,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
fun SwiftSecondaryCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = TelemetrySwiftColors.SecondaryCardBackground,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
fun SwiftSectionTitle(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = TelemetrySwiftColors.TextPrimary,
    )
}

@Composable
fun SwiftCaption(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = TelemetrySwiftColors.TextSecondary,
    )
}

@Composable
fun SwiftPrimaryText(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = TelemetrySwiftColors.TextPrimary,
    )
}

@Composable
fun SwiftMetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TelemetrySwiftColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = TelemetrySwiftColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SwiftProgress(
    value: Float,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { value.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = TelemetrySwiftColors.Accent,
        trackColor = TelemetrySwiftColors.Divider,
    )
}

@Composable
fun SwiftPrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = TelemetrySwiftColors.Accent,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Composable
fun SwiftSecondaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TelemetrySwiftColors.Accent,
        ),
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Composable
fun SwiftScoreCircle(
    score: Double?,
    modifier: Modifier = Modifier,
) {
    val color = scoreColor(score)
    val text = score?.let { "${it.toInt()}" } ?: "—"

    Column(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.14f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "/100",
            color = TelemetrySwiftColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

fun scoreColor(score: Double?): Color {
    return when {
        score == null -> TelemetrySwiftColors.ScoreGray
        score >= 80.0 -> TelemetrySwiftColors.ScoreGreen
        score >= 60.0 -> TelemetrySwiftColors.ScoreYellow
        else -> TelemetrySwiftColors.ScoreRed
    }
}