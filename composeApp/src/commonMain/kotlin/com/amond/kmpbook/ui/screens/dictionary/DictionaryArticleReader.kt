package com.amond.kmpbook.ui.screens.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.dictionary.content.DictionaryArticle
import com.amond.kmpbook.dictionary.content.DictionaryBlockKind
import com.amond.kmpbook.dictionary.content.DictionaryContentBlock
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.VisibleVerticalScrollbar
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

@Composable
internal fun DictionaryArticleReader(article: DictionaryArticle, onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
    ) {
        LedgerPanel(Modifier.fillMaxWidth().height(64.dp), padding = MarketSpacing.xs) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(MarketSpacing.xs))
                    Text("사전으로", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.width(MarketSpacing.sm))
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = MarketSpacing.xs)
                        .background(MarketColors.Line),
                )
                Spacer(Modifier.width(MarketSpacing.md))
                Text(
                    text = article.category.displayName,
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.PrimaryText,
                )
                Spacer(Modifier.width(MarketSpacing.sm))
                Text(article.title, style = MarketType.label, color = MarketColors.InkMuted)
            }
        }
        LedgerPanel(Modifier.fillMaxWidth().weight(1f), padding = 0.dp) {
            VisibleVerticalScrollbar(state = scrollState, modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = MarketSpacing.xxl, vertical = MarketSpacing.xl),
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 920.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MarketSpacing.md),
                    ) {
                        Text(
                            text = article.category.displayName.uppercase(),
                            style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = MarketColors.PrimaryText,
                        )
                        Text(
                            text = article.title,
                            style = MarketType.display.copy(fontWeight = FontWeight.Bold),
                            color = MarketColors.Ink,
                        )
                        Text(text = article.summary, style = MarketType.body, color = MarketColors.InkMuted)
                        if (article.tags.isNotEmpty()) {
                            Text(
                                text = article.tags.joinToString("  ·  "),
                                style = MarketType.caption,
                                color = MarketColors.Grey400,
                            )
                        }
                        LedgerDivider(Modifier.padding(vertical = MarketSpacing.xs))
                        article.blocks
                            .dropFirstTitleHeading(article.title)
                            .forEach { block -> DictionaryBlock(block) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryBlock(block: DictionaryContentBlock) {
    when (block.kind) {
        DictionaryBlockKind.HEADING -> Text(
            text = block.text,
            modifier = Modifier.padding(top = if (block.level == 2) MarketSpacing.md else MarketSpacing.xs),
            style = when (block.level) {
                1 -> MarketType.headingLarge.copy(fontWeight = FontWeight.Bold)
                2 -> MarketType.heading.copy(fontWeight = FontWeight.Bold)
                else -> MarketType.body.copy(fontWeight = FontWeight.SemiBold)
            },
            color = MarketColors.Ink,
        )
        DictionaryBlockKind.PARAGRAPH -> Text(
            text = block.text,
            style = MarketType.body,
            color = MarketColors.Ink,
        )
        DictionaryBlockKind.BULLET -> DictionaryBullet(block.text)
        DictionaryBlockKind.QUOTE -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MarketColors.PrimaryWeak, RoundedCornerShape(MarketRadii.medium))
                .border(1.dp, MarketColors.SignalLine.copy(alpha = 0.48f), RoundedCornerShape(MarketRadii.medium))
                .padding(MarketSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .background(MarketColors.Primary, RoundedCornerShape(MarketRadii.pill)),
            )
            Text(block.text, modifier = Modifier.weight(1f), style = MarketType.body, color = MarketColors.Ink)
        }
        DictionaryBlockKind.DIVIDER -> LedgerDivider(Modifier.padding(vertical = MarketSpacing.xs))
    }
}

@Composable
private fun DictionaryBullet(text: String) {
    val uriHandler = LocalUriHandler.current
    val urlStart = text.indexOf("http")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(5.dp)
                .background(MarketColors.Primary, RoundedCornerShape(MarketRadii.pill)),
        )
        if (urlStart >= 0) {
            val label = text.substring(0, urlStart).trimEnd(' ', '—', '-')
            val url = text.substring(urlStart).trim()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button) { uriHandler.openUri(url) }
                    .semantics { contentDescription = "${label.ifBlank { url }} 참고 자료 열기" },
                verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs),
            ) {
                if (label.isNotBlank()) Text(label, style = MarketType.body, color = MarketColors.Ink)
                Text(
                    text = url,
                    style = MarketType.label.copy(fontWeight = FontWeight.Medium),
                    color = MarketColors.PrimaryText,
                )
            }
        } else {
            Text(text, modifier = Modifier.weight(1f), style = MarketType.body, color = MarketColors.Ink)
        }
    }
}

private fun List<DictionaryContentBlock>.dropFirstTitleHeading(title: String): List<DictionaryContentBlock> =
    if (firstOrNull()?.let { it.kind == DictionaryBlockKind.HEADING && it.level == 1 && it.text == title } == true) {
        drop(1)
    } else {
        this
    }
