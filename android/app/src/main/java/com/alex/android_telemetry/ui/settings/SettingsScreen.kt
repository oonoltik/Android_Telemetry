package com.alex.android_telemetry.ui.settings

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
import com.alex.android_telemetry.core.localization.AppLanguage
import com.alex.android_telemetry.ui.design.TelemetrySpacing
import com.alex.android_telemetry.ui.design.TelemetrySwiftColors
import com.alex.android_telemetry.ui.design.TelemetryTypography
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
@Composable
fun SettingsScreen(
    currentDriverId: String?,
    onDone: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenDriverAccount: () -> Unit,
    onOpenPermissionsBackground: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDeleteLocalData: () -> Unit,
    onDeleteAccount: () -> Unit,
    currentLanguage: AppLanguage,
    onLanguageChanged: (AppLanguage) -> Unit,
) {
    var isLanguageMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TelemetrySwiftColors.ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TelemetrySpacing.ScreenHorizontal)
            .padding(top = 10.dp, bottom = 28.dp),
    ) {
        SettingsNavigationBar(
            onDone = onDone,
            onOpenGuide = onOpenGuide,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.settings_title),
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.LargeTitle,
        )

        Spacer(Modifier.height(26.dp))

        SettingsSectionTitle(stringResource(R.string.settings_profile))

        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.settings_driver),
                value = currentDriverId?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.settings_driver_not_selected),
                onClick = onOpenDriverAccount,
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_change_name),
                value = null,
                onClick = onOpenDriverAccount,
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsSectionTitle(stringResource(R.string.settings_app))

        SettingsGroup {
            SettingsLanguageRow(
                currentLanguage = currentLanguage,
                isExpanded = isLanguageMenuExpanded,
                onExpandedChange = { isLanguageMenuExpanded = it },
                onLanguageChanged = onLanguageChanged,
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_permissions_background),
                value = stringResource(R.string.settings_check),
                onClick = onOpenPermissionsBackground,
            )
        }

        SettingsFootnote(
            text = stringResource(R.string.settings_permissions_footnote),
        )

        Spacer(Modifier.height(24.dp))

//        SettingsSectionTitle(stringResource(R.string.settings_system))
//
//        SettingsGroup {
//            SettingsRow(
//                title = stringResource(R.string.settings_diagnostics),
//                value = stringResource(R.string.settings_for_developer),
//                onClick = onOpenDiagnostics,
//            )
//        }
//
//        SettingsFootnote(
//            text = stringResource(R.string.settings_diagnostics_footnote),
//        )
//
//        Spacer(Modifier.height(24.dp))

        SettingsSectionTitle(stringResource(R.string.settings_data))

        SettingsGroup {
            SettingsDestructiveRow(
                title = stringResource(R.string.settings_delete_local_data),
                onClick = onDeleteLocalData,
            )

            SettingsDivider()

            SettingsDestructiveRow(
                title = stringResource(R.string.settings_delete_account),
                onClick = onDeleteAccount,
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Android Telemetry",
            modifier = Modifier.fillMaxWidth(),
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Caption,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsLanguageRow(
    currentLanguage: AppLanguage,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onExpandedChange(true)
            }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_language),
            modifier = Modifier.weight(1f),
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.Body,
        )

        Box(
            contentAlignment = Alignment.TopEnd,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (currentLanguage) {
                        AppLanguage.Russian -> stringResource(R.string.language_russian)
                        AppLanguage.English -> stringResource(R.string.language_english)
                    },
                    color = TelemetrySwiftColors.TextSecondary,
                    style = TelemetryTypography.Body,
                    textAlign = TextAlign.End,
                )

                Text(
                    text = "  ›",
                    color = TelemetrySwiftColors.TextSecondary,
                    style = TelemetryTypography.Body,
                )
            }

            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = {
                    onExpandedChange(false)
                },
            ) {
                AppLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = when (language) {
                                    AppLanguage.Russian -> stringResource(R.string.language_russian)
                                    AppLanguage.English -> stringResource(R.string.language_english)
                                },
                            )
                        },
                        onClick = {
                            onExpandedChange(false)

                            if (language != currentLanguage) {
                                onLanguageChanged(language)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationBar(
    onDone: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        TextButton(
            onClick = onOpenGuide,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFFEAF3FF))
                .border(
                    width = 2.dp,
                    color = Color(0xFF0A84FF),
                    shape = CircleShape,
                )
        ) {
            Text(
                text = "?",
                color = Color.Black,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        TextButton(
            onClick = onDone,
        ) {
            Text(
                text = stringResource(R.string.settings_done),
                color = Color(0xFF0A84FF),
                style = TelemetryTypography.BodyEmphasis,
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(
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
private fun SettingsGroup(
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
private fun SettingsRow(
    title: String,
    value: String?,
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
            text = title,
            modifier = Modifier.weight(1f),
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.Body,
        )

        if (value != null) {
            Text(
                text = value,
                color = TelemetrySwiftColors.TextSecondary,
                style = TelemetryTypography.Body,
                textAlign = TextAlign.End,
            )
        }

        Text(
            text = "  ›",
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Body,
        )
    }
}

@Composable
private fun SettingsDestructiveRow(
    title: String,
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
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(0xFFFF3B30),
            style = TelemetryTypography.Body,
        )
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(
        modifier = Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(TelemetrySwiftColors.Divider),
    )
}

@Composable
private fun SettingsFootnote(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
        color = TelemetrySwiftColors.TextSecondary,
        style = TelemetryTypography.Caption,
    )
}