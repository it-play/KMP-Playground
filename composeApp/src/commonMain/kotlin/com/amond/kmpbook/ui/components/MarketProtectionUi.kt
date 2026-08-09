package com.amond.kmpbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.presentation.protection.MarketProtectionStripUi
import com.amond.kmpbook.presentation.protection.ProtectionBadgeEmphasis
import com.amond.kmpbook.presentation.protection.ProtectionDetailUi
import com.amond.kmpbook.presentation.protection.ProtectionStatusBadgeUi
import com.amond.kmpbook.presentation.protection.ProtectionUiStatus
import com.amond.kmpbook.presentation.protection.ProtectionUiTone
import com.amond.kmpbook.ui.format.formatDateTimeKst
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType

/*
 * 공개 TDS의 Badge(상태 인지), ListRow(정보 위계), BottomSheet(점진적 공개) 패턴을
 * 데스크톱 Compose에 맞게 옮겼다.
 * https://tossmini-docs.toss.im/tds-mobile/components/badge/
 * https://tossmini-docs.toss.im/tds-react-native/components/list-row/
 * https://tossmini-docs.toss.im/tds-mobile/hooks/OverlayExtension/use-bottom-sheet/
 */

/**
 * 시장 전체 거래 제한이 실제로 작동할 때만 나타나는 한 줄 상태 스트립.
 * 애니메이션이나 점멸 없이, 가장 강한 상태 하나와 나머지 개수만 먼저 보여 준다.
 */
@Composable
fun MarketProtectionStrip(
    model: MarketProtectionStripUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (model == null) return
    val colors = protectionColors(model.badge.tone)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                stateDescription = model.stateDescription
                liveRegion = LiveRegionMode.Polite
            },
        color = colors.weakBackground,
        shape = RoundedCornerShape(MarketRadii.medium),
    ) {
        Row(
            modifier = Modifier.padding(end = MarketSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 64.dp)
                    .background(colors.accent),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MarketSpacing.md, vertical = MarketSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
            ) {
                ProtectionBadgeVisual(model.badge)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = model.title,
                        style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                        color = MarketColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = model.summary,
                        style = MarketType.caption,
                        color = MarketColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "자세히  ›",
                    style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 실제 배지는 작게 유지하고, 클릭 가능한 경우 바깥 hit area만 44dp로 넓힌다. */
@Composable
fun ProtectionStatusBadge(
    model: ProtectionStatusBadgeUi?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (model == null) return
    val semanticModifier = Modifier.semantics(mergeDescendants = true) {
        stateDescription = model.stateDescription
    }
    if (onClick == null) {
        Box(
            modifier = modifier.then(semanticModifier),
            contentAlignment = Alignment.Center,
        ) {
            ProtectionBadgeVisual(model)
        }
    } else {
        Box(
            modifier = modifier
                .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .then(semanticModifier),
            contentAlignment = Alignment.Center,
        ) {
            ProtectionBadgeVisual(model)
        }
    }
}

/**
 * 데스크톱의 우측 패널이나 Dialog 안에 바로 넣을 수 있는 선택 종목 상세 표면.
 * 핵심 상태의 원인·주문 영향·재개 조건을 먼저 읽고, 중복 상태는 아래에서만 펼친다.
 */
@Composable
fun MarketProtectionDetailSurface(
    model: ProtectionDetailUi,
    modifier: Modifier = Modifier,
    contextTitle: String? = null,
    onClose: (() -> Unit)? = null,
) {
    LedgerPanel(modifier = modifier, padding = 0.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(
                    start = MarketSpacing.lg,
                    top = MarketSpacing.lg,
                    end = MarketSpacing.sm,
                    bottom = MarketSpacing.md,
                ),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MarketSpacing.xs),
                ) {
                    Text(
                        text = model.contextLabel,
                        style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = MarketColors.Primary,
                    )
                    Text(
                        text = contextTitle ?: model.primary.title,
                        style = MarketType.headingLarge,
                        color = MarketColors.Ink,
                    )
                    ProtectionStatusBadge(model.badge)
                }
                if (onClose != null) {
                    Box(
                        modifier = Modifier
                            .size(MarketComponentSize.minimumInteractiveTarget)
                            .clickable(role = Role.Button, onClick = onClose)
                            .semantics { contentDescription = "거래 상태 상세 닫기" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "닫기",
                            style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                            color = MarketColors.InkMuted,
                        )
                    }
                }
            }

            LedgerDivider()
            Column(
                modifier = Modifier.padding(horizontal = MarketSpacing.lg, vertical = MarketSpacing.xs),
            ) {
                ProtectionDetailRow(
                    label = "무슨 일이에요",
                    value = "${model.primary.title}\n${model.primary.summary}",
                )
                LedgerDivider()
                ProtectionDetailRow(
                    label = "주문은 어떻게 돼요",
                    value = model.primary.orderImpact,
                )
                LedgerDivider()
                ProtectionDetailRow(
                    label = "언제 다시 거래돼요",
                    value = model.primary.resumeGuidance.withExpectedTime(model.primary),
                )
                LedgerDivider()
                ProtectionDetailRow(
                    label = "적용 규칙",
                    value = model.primary.ruleExplanation,
                )
            }

            if (model.additional.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MarketColors.PaperMuted)
                        .padding(horizontal = MarketSpacing.lg, vertical = MarketSpacing.sm),
                ) {
                    Text(
                        text = "함께 적용 중인 상태 ${model.additionalCount}개",
                        style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                        color = MarketColors.InkMuted,
                    )
                }
                Column(Modifier.padding(horizontal = MarketSpacing.lg, vertical = MarketSpacing.xs)) {
                    model.additional.forEachIndexed { index, status ->
                        AdditionalProtectionRow(status)
                        if (index != model.additional.lastIndex) LedgerDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionBadgeVisual(model: ProtectionStatusBadgeUi) {
    val colors = protectionColors(model.tone)
    val filled = model.emphasis == ProtectionBadgeEmphasis.FILL
    Row(
        modifier = Modifier
            .background(
                color = if (filled) colors.accent else colors.weakBackground,
                shape = RoundedCornerShape(MarketRadii.pill),
            )
            .padding(horizontal = MarketSpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = model.text,
            style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (filled) Color.White else colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProtectionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MarketSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MarketSpacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(132.dp),
            style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.Ink,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MarketType.body,
            color = MarketColors.InkMuted,
        )
    }
}

@Composable
private fun AdditionalProtectionRow(status: ProtectionUiStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MarketSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProtectionBadgeVisual(status.asSingleBadge())
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = status.title,
                style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status.summary,
                style = MarketType.caption,
                color = MarketColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class ProtectionColors(
    val accent: Color,
    val weakBackground: Color,
)

private fun protectionColors(tone: ProtectionUiTone): ProtectionColors = when (tone) {
    ProtectionUiTone.INFO -> ProtectionColors(MarketColors.Primary, MarketColors.PrimaryWeak)
    ProtectionUiTone.CAUTION -> ProtectionColors(MarketColors.Amber, MarketColors.AmberSoft)
    ProtectionUiTone.CRITICAL -> ProtectionColors(MarketColors.Rise, MarketColors.RiseSoft)
}

private fun ProtectionUiStatus.asSingleBadge(): ProtectionStatusBadgeUi = ProtectionStatusBadgeUi(
    text = badgeLabel,
    tone = tone,
    emphasis = emphasis,
    additionalCount = 0,
    stateDescription = "$badgeLabel: $summary",
)

private fun String.withExpectedTime(status: ProtectionUiStatus): String = status.endsAt?.let {
    "$this\n예정 ${formatDateTimeKst(it)} KST"
} ?: this
