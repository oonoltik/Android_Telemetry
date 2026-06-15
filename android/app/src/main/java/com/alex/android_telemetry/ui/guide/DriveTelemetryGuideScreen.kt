package com.alex.android_telemetry.ui.guide

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.android_telemetry.R
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.offset

private data class GuideSlide(
    @DrawableRes val landscapeImageRes: Int,
    @DrawableRes val portraitImageRes: Int,
)

private val guideSlides =
    listOf(
        GuideSlide(
            landscapeImageRes = R.drawable.guide_slide_1,
            portraitImageRes = R.drawable.guide_slide_1_portrait,
        ),
        GuideSlide(
            landscapeImageRes = R.drawable.guide_slide_2,
            portraitImageRes = R.drawable.guide_slide_2_portrait,
        ),
        GuideSlide(
            landscapeImageRes = R.drawable.guide_slide_3,
            portraitImageRes = R.drawable.guide_slide_3_portrait,
        ),
        GuideSlide(
            landscapeImageRes = R.drawable.guide_slide_4,
            portraitImageRes = R.drawable.guide_slide_4_portrait,
        ),
        GuideSlide(
            landscapeImageRes = R.drawable.guide_slide_5,
            portraitImageRes = R.drawable.guide_slide_5_portrait,
        ),
        GuideSlide(
            landscapeImageRes = R.drawable.guide_slide_6,
            portraitImageRes = R.drawable.guide_slide_6_portrait,
        ),
        GuideSlide(
            landscapeImageRes = R.drawable.guide_slide_7_no_score,
            portraitImageRes = R.drawable.guide_slide_7_portrait_no_score,
        ),
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DriveTelemetryGuideScreen(
    onBack: () -> Unit,
    onStartFirstTrip: () -> Unit,
) {
    val pagerState =
        rememberPagerState(
            pageCount = {
                guideSlides.size
            },
        )

    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == guideSlides.lastIndex

    val configuration = LocalConfiguration.current
    val isPortrait =
        configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var demoRating by rememberSaveable {
        mutableIntStateOf(90)
    }

    var lastOrientation by rememberSaveable {
        mutableIntStateOf(configuration.orientation)
    }

    LaunchedEffect(currentPage) {
        if (currentPage != guideSlides.lastIndex) {
            demoRating = 90
            lastOrientation = configuration.orientation
        }
    }

    LaunchedEffect(configuration.orientation, currentPage) {
        if (currentPage == guideSlides.lastIndex &&
            configuration.orientation != lastOrientation
        ) {
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
            val imageRes =
                if (isPortrait) {
                    slide.portraitImageRes
                } else {
                    slide.landscapeImageRes
                }

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )

                if (page == guideSlides.lastIndex) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .align(
                                        if (isPortrait) {
                                            Alignment.TopCenter
                                        } else {
                                            Alignment.Center
                                        },
                                    )
                                    .offset(
                                        y =
                                            if (isPortrait) {
                                                (-40).dp
                                            } else {
                                                (-20).dp
                                            },
                                    )
                                    .size(
                                        if (isPortrait) {
                                            220.dp
                                        } else {
                                            180.dp
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = demoRating.toString(),
                                    color = Color(0xFF55F27A),
                                    fontSize =
                                        if (isPortrait) {
                                            86.sp
                                        } else {
                                            70.sp
                                        },
                                    fontWeight = FontWeight.Bold,
                                )

                                Text(
                                    text = "из 100",
                                    color = Color.White,
                                    fontSize =
                                        if (isPortrait) {
                                            26.sp
                                        } else {
                                            22.sp
                                        },
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
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
            TextButton(
                onClick = onBack,
            ) {
                Text(
                    text = "Закрыть",
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
                            text = "Назад",
                            textAlign = TextAlign.Center,
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
                                "Получить рейтинг"
                            } else {
                                "Далее"
                            },
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
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
                            horizontal =
                                if (isSelected) {
                                    10.dp
                                } else {
                                    5.dp
                                },
                            vertical = 5.dp,
                        ),
            )
        }
    }
}