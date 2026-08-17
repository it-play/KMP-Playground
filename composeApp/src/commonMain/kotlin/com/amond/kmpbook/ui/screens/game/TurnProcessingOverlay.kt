package com.amond.kmpbook.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.domain.model.game.TurnStep
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.presentation.simulator.TurnProcessingUiState
import com.amond.kmpbook.ui.components.LoadingFinancialFact
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonTone
import com.amond.kmpbook.ui.components.MarketButtonVariant
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * Blocking market-clock view shown while a turn command owns the simulator runtime.
 * The tape and counters are driven by committed simulated hours, never an estimated timer.
 */
@Composable
fun TurnProcessingOverlay(
    state: TurnProcessingUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MarketColors.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(MarketSpacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxWidth(),
            color = MarketColors.NavyRaised,
            shape = RoundedCornerShape(MarketRadii.large),
            shadowElevation = 10.dp,
        ) {
            Column {
                MarketClockHeader(state)
                MarketClockTimeline(
                    state = state,
                    modifier = Modifier.padding(
                        horizontal = MarketSpacing.xl,
                        vertical = MarketSpacing.lg,
                    ),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MarketColors.Grey700.copy(alpha = 0.72f)),
                )
                BoxWithConstraints(
                    modifier = Modifier.padding(MarketSpacing.xl),
                ) {
                    if (maxWidth < 620.dp) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MarketSpacing.lg),
                        ) {
                            ProcessingClock(state)
                            ProcessingDetail(state, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MarketSpacing.xxl),
                        ) {
                            ProcessingClock(state)
                            ProcessingDetail(state, Modifier.weight(1f))
                        }
                    }
                }
                ProcessingFooter(state = state, onCancel = onCancel)
            }
        }
    }
}

@Composable
private fun MarketClockHeader(state: TurnProcessingUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MarketColors.Navy)
            .padding(horizontal = MarketSpacing.xl, vertical = MarketSpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs)) {
            Text(
                text = "MARKET CLOCK  /  TURN PROCESSING",
                style = MarketType.caption.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
                color = MarketColors.SignalLine,
            )
            Text(
                text = formatDateTimeKst(state.currentTime),
                style = MarketType.numberLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
        Surface(
            color = MarketColors.Signal.copy(alpha = 0.18f),
            shape = RoundedCornerShape(MarketRadii.pill),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MarketColors.SignalLine.copy(alpha = 0.55f),
            ),
        ) {
            Text(
                text = state.step.displayName,
                modifier = Modifier.padding(horizontal = MarketSpacing.md, vertical = MarketSpacing.xs),
                style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.SignalLine,
            )
        }
    }
}

@Composable
private fun MarketClockTimeline(
    state: TurnProcessingUiState,
    modifier: Modifier = Modifier,
) {
    val checkpoints = remember(state.startedAt, state.targetTime, state.step) {
        timelineCheckpoints(state)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "시작  ${formatDateTimeKst(state.startedAt)}",
                style = MarketType.caption,
                color = MarketColors.Grey400,
            )
            Text(
                text = "목표  ${formatDateTimeKst(state.targetTime)}",
                style = MarketType.caption,
                color = MarketColors.Grey200,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
                    contentDescription =
                        "시장 시간 ${state.completedHours}시간 처리, 전체 ${state.totalHours}시간"
                },
            contentAlignment = Alignment.Center,
        ) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .height(4.dp),
                color = MarketColors.SignalLine,
                trackColor = MarketColors.Grey700,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                checkpoints.forEach { (position, _) ->
                    val reached = position <= state.progress + PROGRESS_EPSILON
                    Box(
                        Modifier
                            .size(if (reached) 12.dp else 10.dp)
                            .background(
                                color = if (reached) MarketColors.SignalLine else MarketColors.NavyRaised,
                                shape = CircleShape,
                            )
                            .border(
                                width = 2.dp,
                                color = if (reached) MarketColors.SignalLine else MarketColors.Grey600,
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            checkpoints.forEachIndexed { index, (_, instant) ->
                Text(
                    text = timelineLabel(instant, state.step),
                    modifier = Modifier.weight(1f),
                    style = MarketType.caption,
                    color = if (index == 0 || index == checkpoints.lastIndex) {
                        MarketColors.Grey200
                    } else {
                        MarketColors.Grey400
                    },
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        checkpoints.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ProcessingClock(state: TurnProcessingUiState) {
    Column(
        modifier = Modifier.width(154.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(126.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
                    contentDescription = "턴 처리 진행률 ${(state.progress * 100).toInt()}퍼센트"
                },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(122.dp),
                color = MarketColors.SignalLine.copy(alpha = 0.30f),
                strokeWidth = 2.dp,
            )
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(102.dp),
                color = MarketColors.SignalLine,
                trackColor = MarketColors.Grey700,
                strokeWidth = 7.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    style = MarketType.numberLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = "COMMITTED",
                    style = MarketType.caption.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                    ),
                    color = MarketColors.Grey400,
                )
            }
        }
        Text(
            text = "${state.completedHours} / ${state.totalHours}시간",
            style = MarketType.number.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.Grey200,
        )
    }
}

@Composable
private fun ProcessingDetail(
    state: TurnProcessingUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.xs),
    ) {
        Text(
            text = if (state.cancellationRequested) "CANCEL BOUNDARY" else "CURRENT OPERATION",
            style = MarketType.caption.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            ),
            color = if (state.cancellationRequested) MarketColors.Amber else MarketColors.SignalLine,
        )
        Text(
            text = state.stage,
            style = MarketType.heading.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Text(
            text = state.latestActivity,
            style = MarketType.body,
            color = MarketColors.Grey200,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MarketSpacing.xs),
            color = MarketColors.Navy,
            shape = RoundedCornerShape(MarketRadii.medium),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MarketColors.Grey700.copy(alpha = 0.9f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(MarketSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MarketSpacing.xs),
            ) {
                Text(
                    text = state.marketSessionSummary,
                    style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.Grey200,
                )
                if (state.recentEventTitle != null) {
                    Text(
                        text = "최근 이벤트  ·  ${state.recentEventTitle}",
                        style = MarketType.caption,
                        color = MarketColors.SignalLine,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "새 시장 이벤트를 감시하고 있습니다.",
                        style = MarketType.caption,
                        color = MarketColors.Grey400,
                    )
                }
            }
        }
        LoadingFinancialFact(
            factKey = "turn:${state.startedAt}",
            modifier = Modifier.padding(top = MarketSpacing.xs),
            dark = true,
            compact = true,
        )
    }
}

@Composable
private fun ProcessingFooter(
    state: TurnProcessingUiState,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MarketColors.Navy)
            .padding(horizontal = MarketSpacing.xl, vertical = MarketSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs)) {
            Text(
                text = "현재 ${formatDateTimeKst(state.currentTime)}  ·  남은 시간 ${state.remainingHours}시간",
                style = MarketType.label,
                color = MarketColors.Grey200,
            )
            Text(
                text = if (state.cancellationRequested) {
                    "현재 원자 시간 계산이 끝난 뒤 시작 상태로 복원합니다."
                } else {
                    "취소는 현재 1시간 계산 경계에서 안전하게 적용됩니다."
                },
                style = MarketType.caption,
                color = if (state.cancellationRequested) MarketColors.Amber else MarketColors.Grey400,
            )
        }
        Spacer(Modifier.width(MarketSpacing.lg))
        MarketButton(
            text = if (state.cancellationRequested) "취소 요청됨" else "진행 취소",
            onClick = onCancel,
            enabled = !state.cancellationRequested,
            variant = MarketButtonVariant.Weak,
            tone = MarketButtonTone.Danger,
        )
    }
}

private fun timelineCheckpoints(state: TurnProcessingUiState): List<Pair<Float, Instant>> {
    val divisions = when (state.step) {
        TurnStep.ONE_HOUR -> 1
        TurnStep.FOUR_HOURS -> 4
        TurnStep.TWELVE_HOURS -> 4
        TurnStep.ONE_DAY -> 4
        TurnStep.ONE_WEEK -> 7
    }
    val totalSeconds = (state.targetTime - state.startedAt).inWholeSeconds
    return (0..divisions).map { index ->
        val position = index.toFloat() / divisions
        val elapsedSeconds = totalSeconds * index / divisions
        position to (state.startedAt + elapsedSeconds.seconds)
    }
}

private fun timelineLabel(instant: Instant, step: TurnStep): String {
    val local = instant.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
    return if (step == TurnStep.ONE_WEEK) {
        "${local.month.number}.${local.day}\n${local.dayOfWeek.shortKoreanName()}"
    } else {
        "${local.hour.twoDigits()}:${local.minute.twoDigits()}"
    }
}

private fun DayOfWeek.shortKoreanName(): String = when (this) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private const val PROGRESS_EPSILON: Float = 0.0001f
