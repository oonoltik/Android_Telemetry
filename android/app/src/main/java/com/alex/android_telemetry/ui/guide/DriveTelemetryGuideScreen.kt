package com.alex.android_telemetry.ui.guide

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.android_telemetry.R
import kotlinx.coroutines.launch

private data class GuideSlide(
    @DrawableRes val landscapeBgRes: Int,
    @DrawableRes val portraitBgRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
)

private val guideSlides =
    listOf(
        GuideSlide(R.drawable.guide_bg_1, R.drawable.guide_bg_1_portrait, R.string.guide_1_title, R.string.guide_1_subtitle),
        GuideSlide(R.drawable.guide_bg_2, R.drawable.guide_bg_2_portrait, R.string.guide_2_title, R.string.guide_2_subtitle),
        GuideSlide(R.drawable.guide_bg_3, R.drawable.guide_bg_3_portrait, R.string.guide_3_title, R.string.guide_3_subtitle),
        GuideSlide(R.drawable.guide_bg_4, R.drawable.guide_bg_4_portrait, R.string.guide_4_title, R.string.guide_4_subtitle),
        GuideSlide(R.drawable.guide_bg_5, R.drawable.guide_bg_5_portrait, R.string.guide_5_title, R.string.guide_5_subtitle),
        GuideSlide(R.drawable.guide_bg_6, R.drawable.guide_bg_6_portrait, R.string.guide_6_title, R.string.guide_6_subtitle),
        GuideSlide(R.drawable.guide_bg_7, R.drawable.guide_bg_7_portrait, R.string.guide_7_title, R.string.guide_7_subtitle),
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DriveTelemetryGuideScreen(
    onBack: () -> Unit,
    onStartFirstTrip: () -> Unit,
) {
    val pagerState =
        rememberPagerState(
            pageCount = { guideSlides.size },
        )

    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == guideSlides.lastIndex

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var demoRating by rememberSaveable { mutableIntStateOf(90) }
    var lastOrientation by rememberSaveable { mutableIntStateOf(configuration.orientation) }

    LaunchedEffect(currentPage) {
        if (currentPage != guideSlides.lastIndex) {
            demoRating = 90
            lastOrientation = configuration.orientation
        }
    }

    LaunchedEffect(configuration.orientation, currentPage) {
        if (currentPage == guideSlides.lastIndex && configuration.orientation != lastOrientation) {
            lastOrientation = configuration.orientation
            demoRating = (demoRating + 1).coerceAtMost(99)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val slide = guideSlides[page]
            val bgRes =
                if (isPortrait) {
                    slide.portraitBgRes
                } else {
                    slide.landscapeBgRes
                }

            GuidePage(
                page = page,
                pageCount = guideSlides.size,
                bgRes = bgRes,
                title = stringResource(slide.titleRes),
                subtitle = stringResource(slide.subtitleRes),
                isPortrait = isPortrait,
                demoRating = demoRating,
            )
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.guide_close),
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "${currentPage + 1}/${guideSlides.size}",
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GuideDots(
                currentPage = currentPage,
                pageCount = guideSlides.size,
            )

            Spacer(modifier = Modifier.padding(top = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (currentPage > 0) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(currentPage - 1)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.guide_back),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isLastPage) {
                            onStartFirstTrip()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0A84FF),
                            contentColor = Color.White,
                        ),
                ) {
                    Text(
                        text =
                            if (isLastPage) {
                                stringResource(R.string.guide_get_rating)
                            } else {
                                stringResource(R.string.guide_next)
                            },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidePage(
    page: Int,
    pageCount: Int,
    @DrawableRes bgRes: Int,
    title: String,
    subtitle: String,
    isPortrait: Boolean,
    demoRating: Int,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(bgRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isPortrait) 0.36f else 0.28f)),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        start = if (isPortrait) 28.dp else 84.dp,
                        end = if (isPortrait) 28.dp else 84.dp,
                        top = if (isPortrait) 60.dp else 10.dp,
                        bottom = if (isPortrait) 90.dp else 90.dp,
                    ),
        ) {
            Text(
                text = (page + 1).toString(),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A84FF))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.padding(top = 16.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = if (isPortrait) 34.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = if (isPortrait) 40.sp else 34.sp,
            )

            Spacer(modifier = Modifier.padding(top = 12.dp))

            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = if (isPortrait) 19.sp else 18.sp,
                lineHeight = if (isPortrait) 27.sp else 24.sp,
            )

            if (page == 6) {
                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconLabel("🛡", stringResource(R.string.guide_rating_safety), isPortrait)
                    IconLabel("👁", stringResource(R.string.guide_rating_attention), isPortrait)
                    IconLabel("🛞", stringResource(R.string.guide_rating_smoothness), isPortrait)
                    IconLabel("🏁", stringResource(R.string.guide_rating_speed), isPortrait)
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))

                when (page) {
                    1 -> SafetyCards(isPortrait)
                    2 -> FatigueCards(isPortrait)
                    3 -> CrashCards(isPortrait)
                    4 -> DashcamCards(isPortrait)
                    5 -> FamilyCards(isPortrait)
                    else -> BasicIcons(isPortrait)
                }
            }
        }
        if (page == 6) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(
                            top =
                                if (isPortrait) {
                                    320.dp
                                } else {
                                    38.dp
                                },
                            end =
                                if (isPortrait) {
                                    0.dp
                                } else {
                                    96.dp
                                },
                        ),
                contentAlignment =
                    if (isPortrait) {
                        Alignment.TopCenter
                    } else {
                        Alignment.TopEnd
                    },
            ) {
                RatingCircle(
                    rating = demoRating,
                    size =
                        if (isPortrait) {
                            170.dp
                        } else {
                            160.dp
                        },
                )
            }
        }
    }
}

@Composable
private fun BasicIcons(isPortrait: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconLabel("📹", stringResource(R.string.guide_icon_video), isPortrait)
        IconLabel("🚗", stringResource(R.string.guide_icon_driving), isPortrait)
        IconLabel("🛡", stringResource(R.string.guide_icon_safety), isPortrait)
        IconLabel("📊", stringResource(R.string.guide_icon_stats), isPortrait)
    }
}

@Composable
private fun SafetyCards(isPortrait: Boolean) {
    if (isPortrait) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard("⚠️", stringResource(R.string.guide_safety_braking), isPortrait)
            FeatureCard("🚀", stringResource(R.string.guide_safety_acceleration), isPortrait)
            FeatureCard("↪️", stringResource(R.string.guide_safety_turns), isPortrait)
            FeatureCard("🏁", stringResource(R.string.guide_safety_speeding), isPortrait)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactFeatureCard("⚠️", stringResource(R.string.guide_safety_braking), Modifier.weight(1f))
            CompactFeatureCard("🚀", stringResource(R.string.guide_safety_acceleration), Modifier.weight(1f))
            CompactFeatureCard("↪️", stringResource(R.string.guide_safety_turns), Modifier.weight(1f))
            CompactFeatureCard("🏁", stringResource(R.string.guide_safety_speeding), Modifier.weight(1f))
        }
    }
}

@Composable
private fun FatigueCards(isPortrait: Boolean) {
    if (isPortrait) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard("🙂", stringResource(R.string.guide_fatigue_normal), isPortrait, Color(0xFF55F27A))
            FeatureCard("😟", stringResource(R.string.guide_fatigue_warning), isPortrait, Color(0xFFFFC247))
            FeatureCard("😴", stringResource(R.string.guide_fatigue_microsleep), isPortrait, Color(0xFFFF453A))
            FeatureCard("🚨", stringResource(R.string.guide_fatigue_alert), isPortrait, Color(0xFFFF453A))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactFeatureCard("🙂", stringResource(R.string.guide_fatigue_normal), Modifier.weight(1f), Color(0xFF55F27A))
            CompactFeatureCard("😟", stringResource(R.string.guide_fatigue_warning), Modifier.weight(1f), Color(0xFFFFC247))
            CompactFeatureCard("😴", stringResource(R.string.guide_fatigue_microsleep), Modifier.weight(1f), Color(0xFFFF453A))
            CompactFeatureCard("🚨", stringResource(R.string.guide_fatigue_alert), Modifier.weight(1f), Color(0xFFFF453A))
        }
    }
}

@Composable
private fun CrashCards(isPortrait: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconLabel("🎥", stringResource(R.string.guide_crash_before), isPortrait)
        IconLabel("🛡", stringResource(R.string.guide_crash_protected), isPortrait)
        IconLabel("🎥", stringResource(R.string.guide_crash_after), isPortrait)
    }
}

@Composable
private fun DashcamCards(isPortrait: Boolean) {
    if (isPortrait) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard("📷", stringResource(R.string.guide_dashcam_road), isPortrait)
            FeatureCard("👤", stringResource(R.string.guide_dashcam_driver), isPortrait)
            FeatureCard("📁", stringResource(R.string.guide_dashcam_archive), isPortrait)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactFeatureCard("📷", stringResource(R.string.guide_dashcam_road), Modifier.weight(1f))
            CompactFeatureCard("👤", stringResource(R.string.guide_dashcam_driver), Modifier.weight(1f))
            CompactFeatureCard("📁", stringResource(R.string.guide_dashcam_archive), Modifier.weight(1f))
        }
    }
}

@Composable
private fun FamilyCards(isPortrait: Boolean) {
    if (isPortrait) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureCard("🔔", stringResource(R.string.guide_family_notifications), isPortrait)
            FeatureCard("📍", stringResource(R.string.guide_family_route), isPortrait)
            FeatureCard("🛡", stringResource(R.string.guide_family_safety), isPortrait)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactFeatureCard("🔔", stringResource(R.string.guide_family_notifications), Modifier.weight(1f))
            CompactFeatureCard("📍", stringResource(R.string.guide_family_route), Modifier.weight(1f))
            CompactFeatureCard("🛡", stringResource(R.string.guide_family_safety), Modifier.weight(1f))
        }
    }
}


@Composable
private fun RatingCircle(
    rating: Int,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke,
                size = Size(size.toPx(), size.toPx()),
            )
            drawArc(
                color = Color(0xFF55F27A),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke,
                size = Size(size.toPx(), size.toPx()),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = rating.toString(),
                color = Color(0xFF55F27A),
                fontSize =
                    if (size < 180.dp) {
                        46.sp
                    } else {
                        58.sp
                    },
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.guide_rating_out_of),
                color = Color.White,
                fontSize =
                    if (size < 180.dp) {
                        16.sp
                    } else {
                        19.sp
                    },
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "★★★★★",
                color = Color(0xFFFFC247),
                fontSize =
                    if (size < 180.dp) {
                        20.sp
                    } else {
                        24.sp
                    },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CompactFeatureCard(
    icon: String,
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF0A84FF),
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            color = accentColor,
        )

        Spacer(modifier = Modifier.padding(start = 8.dp))

        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun FeatureCard(
    icon: String,
    text: String,
    isPortrait: Boolean,
    accentColor: Color = Color(0xFF0A84FF),
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 16.dp, vertical = if (isPortrait) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            fontSize = if (isPortrait) 26.sp else 22.sp,
            color = accentColor,
        )

        Spacer(modifier = Modifier.padding(start = 12.dp))

        Text(
            text = text,
            color = Color.White,
            fontSize = if (isPortrait) 18.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun IconLabel(
    icon: String,
    label: String,
    isPortrait: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = icon,
            fontSize = if (isPortrait) 28.sp else 24.sp,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(12.dp),
        )

        Spacer(modifier = Modifier.padding(top = 6.dp))

        Text(
            text = label,
            color = Color.White,
            fontSize = if (isPortrait) 13.sp else 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GuideDots(
    currentPage: Int,
    pageCount: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage

            Box(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                Color(0xFF0A84FF)
                            } else {
                                Color.White.copy(alpha = 0.35f)
                            },
                        )
                        .padding(
                            horizontal = if (isSelected) 10.dp else 5.dp,
                            vertical = 5.dp,
                        ),
            )
        }
    }
}