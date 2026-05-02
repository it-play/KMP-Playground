package com.amond.kmpbook.ui.screens.opening

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.ui.components.MarketHistorySlideFrame
import com.amond.kmpbook.ui.components.MarketHistorySlideStatus
import com.amond.kmpbook.ui.theme.MarketType
import com.amond.kmpbook.ui.theme.MarketSimulatorTheme
import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.opening_logo_it_play
import kmpbook.composeapp.generated.resources.opening_logo_jetbrains
import kmpbook.composeapp.generated.resources.opening_logo_kotlin
import kmpbook.composeapp.generated.resources.opening_logo_krx
import kmpbook.composeapp.generated.resources.opening_logo_nasdaq
import kmpbook.composeapp.generated.resources.opening_logo_nyse
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random

@Composable
fun OpeningScreen(
    isLoadingComplete: Boolean,
    loadingStatus: String,
    loadingError: String?,
    loadingErrorTitle: String = "시뮬레이션을 준비하지 못했습니다",
    loadingErrorSecondaryActionLabel: String? = null,
    onLoadingErrorSecondaryAction: () -> Unit = {},
    onRetry: () -> Unit,
    onSlideshowEntered: () -> Unit = {},
    onFinished: () -> Unit,
) {
    var phase by remember { mutableStateOf(OpeningSequencePhase.IT_PLAY) }
    var currentSlideIndex by remember { mutableIntStateOf(0) }
    var finishRequested by remember { mutableStateOf(false) }
    val overlayAlpha = remember { Animatable(1f) }
    val latestLoadingComplete by rememberUpdatedState(isLoadingComplete)
    val latestLoadingError by rememberUpdatedState(loadingError)
    val latestOnSlideshowEntered by rememberUpdatedState(onSlideshowEntered)
    val latestOnFinished by rememberUpdatedState(onFinished)
    val interactionSource = remember { MutableInteractionSource() }
    val slides = remember { openingSlides.shuffled() }
    val targetSlideCount = remember { selectSlideshowSlideCount() }

    LaunchedEffect(Unit) {
        delay(IT_PLAY_HOLD_MILLIS)
        phase = OpeningSequencePhase.BLACKOUT_AFTER_IT_PLAY
        delay(BLACKOUT_MILLIS)
        phase = OpeningSequencePhase.EXCHANGES
        delay(EXCHANGES_HOLD_MILLIS)
        phase = OpeningSequencePhase.BLACKOUT_AFTER_EXCHANGES
        delay(BLACKOUT_MILLIS)
        phase = OpeningSequencePhase.TECHNOLOGY
        delay(TECHNOLOGY_HOLD_MILLIS)
        phase = OpeningSequencePhase.BLACKOUT_AFTER_TECHNOLOGY
        delay(BLACKOUT_MILLIS)
        phase = OpeningSequencePhase.SLIDESHOW
    }
    LaunchedEffect(phase) {
        if (phase != OpeningSequencePhase.SLIDESHOW) return@LaunchedEffect
        latestOnSlideshowEntered()
        var displayedSlideCount = 1
        while (!finishRequested) {
            delay(SLIDE_HOLD_MILLIS)
            val hasReachedTargetSlideCount = displayedSlideCount >= targetSlideCount
            if (
                hasReachedTargetSlideCount &&
                latestLoadingComplete &&
                latestLoadingError == null
            ) {
                finishRequested = true
            } else {
                currentSlideIndex = (currentSlideIndex + 1) % slides.size
                displayedSlideCount += 1
            }
        }
    }
    LaunchedEffect(finishRequested) {
        if (!finishRequested) return@LaunchedEffect
        overlayAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = OPENING_FADE_OUT_MILLIS),
        )
        latestOnFinished()
    }

    MarketSimulatorTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = overlayAlpha.value },
            color = Color.Black,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Crossfade(
                    targetState = phase,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(durationMillis = FRAME_CROSSFADE_MILLIS),
                ) { visiblePhase ->
                    when (visiblePhase) {
                        OpeningSequencePhase.IT_PLAY -> ItPlayFrame()
                        OpeningSequencePhase.EXCHANGES -> ExchangeFrame()
                        OpeningSequencePhase.TECHNOLOGY -> TechnologyFrame()
                        OpeningSequencePhase.SLIDESHOW -> SlideshowFrame(
                            slides = slides,
                            currentSlideIndex = currentSlideIndex,
                            loadingStatus = loadingStatus,
                        )
                        else -> Box(Modifier.fillMaxSize().background(Color.Black))
                    }
                }

                val errorMessage = loadingError
                AnimatedVisibility(
                    visible = phase == OpeningSequencePhase.SLIDESHOW && errorMessage != null,
                    modifier = Modifier.align(Alignment.Center),
                    enter = fadeIn(tween(ERROR_PANEL_FADE_MILLIS)),
                    exit = fadeOut(tween(ERROR_PANEL_FADE_MILLIS)),
                ) {
                    OpeningErrorPanel(
                        title = loadingErrorTitle,
                        error = errorMessage.orEmpty(),
                        onRetry = onRetry,
                        secondaryActionLabel = loadingErrorSecondaryActionLabel,
                        onSecondaryAction = onLoadingErrorSecondaryAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItPlayFrame() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.opening_logo_it_play),
            contentDescription = "IT-Play",
            modifier = Modifier.size(190.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(26.dp))
        Text(
            text = "IT-PLAY",
            style = MarketType.label.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 8.sp,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun ExchangeFrame() {
    val exchangeLogos = listOf(
        Res.drawable.opening_logo_nasdaq,
        Res.drawable.opening_logo_nyse,
        Res.drawable.opening_logo_krx,
    )
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 120.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        exchangeLogos.forEachIndexed { index, logo ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 52.dp)
                        .width(1.dp)
                        .height(116.dp)
                        .background(Color.White.copy(alpha = 0.28f)),
                )
            }
            WhiteLogo(
                resource = logo,
                contentDescription = when (index) {
                    0 -> "Nasdaq"
                    1 -> "New York Stock Exchange"
                    else -> "Korea Exchange"
                },
                modifier = Modifier.width(270.dp).height(104.dp),
            )
        }
    }
}

@Composable
private fun TechnologyFrame() {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WhiteLogo(
            resource = Res.drawable.opening_logo_jetbrains,
            contentDescription = "JetBrains",
            modifier = Modifier.width(300.dp).height(112.dp),
        )
        Box(
            Modifier
                .padding(horizontal = 56.dp)
                .width(1.dp)
                .height(124.dp)
                .background(Color.White.copy(alpha = 0.36f)),
        )
        WhiteLogo(
            resource = Res.drawable.opening_logo_kotlin,
            contentDescription = "Kotlin",
            modifier = Modifier.width(300.dp).height(112.dp),
        )
    }
}

@Composable
private fun WhiteLogo(
    resource: DrawableResource,
    contentDescription: String,
    modifier: Modifier,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun SlideshowFrame(
    slides: List<OpeningSlide>,
    currentSlideIndex: Int,
    loadingStatus: String,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(
            targetState = currentSlideIndex,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(durationMillis = SLIDE_CROSSFADE_MILLIS),
        ) { visibleSlideIndex ->
            val slide = slides[visibleSlideIndex]
            MarketHistorySlideFrame(
                image = slide.image,
                market = slide.market,
                year = slide.year,
                credit = slide.credit,
                factKey = "opening:$visibleSlideIndex",
                statusLabel = "로딩 중",
                statusDescription = loadingStatus,
                showProgress = true,
                showStatus = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        MarketHistorySlideStatus(
            label = "로딩 중",
            description = loadingStatus,
            showProgress = true,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun OpeningErrorPanel(
    title: String,
    error: String,
    onRetry: () -> Unit,
    secondaryActionLabel: String?,
    onSecondaryAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(620.dp)
            .border(1.dp, Color.White.copy(alpha = 0.24f)),
        color = Color.Black.copy(alpha = 0.88f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MarketType.heading,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error,
                style = MarketType.body,
                color = Color.White.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (secondaryActionLabel != null) {
                    TextButton(onClick = onSecondaryAction) {
                        Text(
                            text = secondaryActionLabel,
                            style = MarketType.label.copy(letterSpacing = 1.sp),
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                }
                TextButton(onClick = onRetry) {
                    Text(
                        text = "다시 시도",
                        style = MarketType.label.copy(letterSpacing = 1.sp),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private fun selectSlideshowSlideCount(): Int {
    var ticket = Random.nextInt(SLIDESHOW_SLIDE_COUNT_WEIGHTS.sum())
    SLIDESHOW_SLIDE_COUNT_WEIGHTS.forEachIndexed { index, weight ->
        if (ticket < weight) return MINIMUM_SLIDESHOW_SLIDES + index
        ticket -= weight
    }
    error("슬라이드 장수를 선택하지 못했습니다.")
}

private const val IT_PLAY_HOLD_MILLIS: Long = 3_200L
private const val BLACKOUT_MILLIS: Long = 500L
private const val EXCHANGES_HOLD_MILLIS: Long = 4_200L
private const val TECHNOLOGY_HOLD_MILLIS: Long = 3_600L
private const val SLIDE_HOLD_MILLIS: Long = 6_500L
private const val MINIMUM_SLIDESHOW_SLIDES: Int = 2
private const val MAXIMUM_SLIDESHOW_SLIDES: Int = 8
// Three to five slides end closest to the 27.7-second track; four is the closest and rarest.
private val SLIDESHOW_SLIDE_COUNT_WEIGHTS: IntArray = intArrayOf(
    15, // 2 slides: 15%
    8, // 3 slides: 8%
    2, // 4 slides: 2%
    10, // 5 slides: 10%
    18, // 6 slides: 18%
    25, // 7 slides: 25%
    22, // 8 slides: 22%
)
private const val FRAME_CROSSFADE_MILLIS: Int = 350
private const val SLIDE_CROSSFADE_MILLIS: Int = 900
private const val ERROR_PANEL_FADE_MILLIS: Int = 240
private const val OPENING_FADE_OUT_MILLIS: Int = 700
