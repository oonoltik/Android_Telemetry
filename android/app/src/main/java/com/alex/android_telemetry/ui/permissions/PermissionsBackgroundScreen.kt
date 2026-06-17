package com.alex.android_telemetry.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.android_telemetry.R
import com.alex.android_telemetry.ui.design.TelemetrySpacing
import com.alex.android_telemetry.ui.design.TelemetrySwiftColors
import com.alex.android_telemetry.ui.design.TelemetryTypography

@Composable
fun PermissionsBackgroundScreen(
    onBack: () -> Unit,
    onOpenLocationSettings: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TelemetrySwiftColors.ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TelemetrySpacing.ScreenHorizontal)
            .padding(top = 10.dp, bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.permissions_back),
                    color = Color(0xFF0A84FF),
                    style = TelemetryTypography.BodyEmphasis,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.permissions_title),
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.LargeTitle,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.permissions_description),
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Body,
        )

        Spacer(Modifier.height(24.dp))

        PermissionsHeroCard()

        Spacer(Modifier.height(24.dp))

        PermissionsSectionTitle(stringResource(R.string.permissions_recommended))

        PermissionsGroup {
            PermissionRow(
                icon = "📍",
                title = stringResource(R.string.permissions_location_title),
                subtitle = stringResource(R.string.permissions_location_subtitle),
                status = stringResource(R.string.permissions_check),
                onClick = onOpenLocationSettings,
            )

            PermissionsDivider()

            PermissionRow(
                icon = "🔋",
                title = stringResource(R.string.permissions_background_title),
                subtitle = stringResource(R.string.permissions_background_subtitle),
                status = stringResource(R.string.permissions_check),
                onClick = onOpenBatterySettings,
            )

            PermissionsDivider()

            PermissionRow(
                icon = "⚙",
                title = stringResource(R.string.permissions_app_settings_title),
                subtitle = stringResource(R.string.permissions_app_settings_subtitle),
                status = stringResource(R.string.permissions_open),
                onClick = onOpenAppSettings,
            )
        }

        Spacer(Modifier.height(24.dp))

        PermissionsSectionTitle(stringResource(R.string.permissions_why_title))

        PermissionsGroup {
            ExplanationRow(
                title = "Replay-safe",
                subtitle = stringResource(R.string.permissions_replay_safe_subtitle),
            )

            PermissionsDivider()

            ExplanationRow(
                title = "Offline-safe",
                subtitle = stringResource(R.string.permissions_offline_safe_subtitle),
            )

            PermissionsDivider()

            ExplanationRow(
                title = "Reboot-safe",
                subtitle = stringResource(R.string.permissions_reboot_safe_subtitle),
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(29.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0A84FF),
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.permissions_done),
                style = TelemetryTypography.Headline,
            )
        }
    }
}

@Composable
private fun PermissionsHeroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFEFEFF4))
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "🚘",
            style = TelemetryTypography.ScoreHero,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.permissions_stable_recording_title),
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.Title1,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.permissions_hero_description),
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Body,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionsSectionTitle(
    text: String,
) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(start = 16.dp, bottom = 7.dp),
        color = TelemetrySwiftColors.TextSecondary,
        style = TelemetryTypography.CaptionEmphasis,
    )
}

@Composable
private fun PermissionsGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TelemetrySwiftColors.CardBackground),
        content = content,
    )
}

@Composable
private fun PermissionRow(
    icon: String,
    title: String,
    subtitle: String,
    status: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            modifier = Modifier.padding(end = 12.dp),
            style = TelemetryTypography.Title2,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = TelemetrySwiftColors.TextPrimary,
                style = TelemetryTypography.BodyEmphasis,
            )

            Text(
                text = subtitle,
                color = TelemetrySwiftColors.TextSecondary,
                style = TelemetryTypography.Caption,
            )
        }

        Text(
            text = "$status  ›",
            color = Color(0xFF0A84FF),
            style = TelemetryTypography.Callout,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ExplanationRow(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.BodyEmphasis,
        )

        Text(
            text = subtitle,
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Caption,
        )
    }
}

@Composable
private fun PermissionsDivider() {
    Spacer(
        modifier = Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(TelemetrySwiftColors.Divider),
    )
}