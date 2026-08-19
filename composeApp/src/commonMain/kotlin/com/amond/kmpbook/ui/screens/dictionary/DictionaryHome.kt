package com.amond.kmpbook.ui.screens.dictionary

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.dictionary.content.DictionaryArticle
import com.amond.kmpbook.dictionary.content.DictionaryCategory
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.VisibleVerticalScrollbar
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search

@Composable
internal fun DictionaryHome(
    articles: List<DictionaryArticle>,
    query: String,
    selectedCategory: DictionaryCategory?,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (DictionaryCategory?) -> Unit,
    onArticleSelect: (DictionaryArticle) -> Unit,
) {
    val normalizedQuery = query.trim().lowercase()
    val filteredArticles = remember(articles, normalizedQuery, selectedCategory) {
        articles.filter { article ->
            (selectedCategory == null || article.category == selectedCategory) &&
                (normalizedQuery.isEmpty() || article.matches(normalizedQuery))
        }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
    ) {
        DictionaryHeader()
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
        ) {
            DictionaryCategoryIndex(
                selectedCategory = selectedCategory,
                onCategorySelect = onCategorySelect,
                modifier = Modifier.width(238.dp).fillMaxHeight(),
            )
            DictionaryArticleIndex(
                articles = filteredArticles,
                query = query,
                selectedCategory = selectedCategory,
                onQueryChange = onQueryChange,
                onArticleSelect = onArticleSelect,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun DictionaryHeader() {
    LedgerPanel(Modifier.fillMaxWidth().height(104.dp), background = MarketColors.NavyRaised) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MarketColors.Primary, RoundedCornerShape(MarketRadii.medium)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Lucide.BookOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(MarketSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs)) {
                Text(
                    text = "사전",
                    style = MarketType.headingLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = "금융 개념부터 자산운용사·금융사·은행·대표 종목까지 한곳에서 찾아봅니다.",
                    style = MarketType.body,
                    color = MarketColors.Grey200,
                )
            }
        }
    }
}

@Composable
private fun DictionaryCategoryIndex(
    selectedCategory: DictionaryCategory?,
    onCategorySelect: (DictionaryCategory?) -> Unit,
    modifier: Modifier,
) {
    LedgerPanel(modifier, padding = MarketSpacing.sm) {
        Column(Modifier.fillMaxSize().selectableGroup()) {
            Text("분류 색인", style = MarketType.label.copy(fontWeight = FontWeight.SemiBold), color = MarketColors.Ink)
            Spacer(Modifier.height(MarketSpacing.sm))
            DictionaryCategoryRow(
                label = "전체",
                selected = selectedCategory == null,
                onClick = { onCategorySelect(null) },
            )
            DictionaryCategory.entries.forEach { category ->
                DictionaryCategoryRow(
                    label = category.displayName,
                    selected = selectedCategory == category,
                    onClick = { onCategorySelect(category) },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "교육용 참고 자료이며 특정 자산의 매수·매도를 권하지 않습니다.",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
            )
        }
    }
}

@Composable
private fun DictionaryCategoryRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
            .background(
                if (selected) MarketColors.PrimaryWeak else Color.Transparent,
                RoundedCornerShape(MarketRadii.small),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = MarketSpacing.sm, vertical = MarketSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MarketType.label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) MarketColors.PrimaryText else MarketColors.Ink,
        )
    }
}

@Composable
private fun DictionaryArticleIndex(
    articles: List<DictionaryArticle>,
    query: String,
    selectedCategory: DictionaryCategory?,
    onQueryChange: (String) -> Unit,
    onArticleSelect: (DictionaryArticle) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(query, selectedCategory) {
        listState.scrollToItem(0)
    }
    LedgerPanel(modifier, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(MarketSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "사전 검색" },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    placeholder = { Text("용어, 회사, 종목 또는 태그 검색", style = MarketType.body) },
                    textStyle = MarketType.body,
                    shape = RoundedCornerShape(MarketRadii.medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MarketColors.Ink,
                        unfocusedTextColor = MarketColors.Ink,
                        cursorColor = MarketColors.Primary,
                        focusedBorderColor = MarketColors.Primary,
                        unfocusedBorderColor = MarketColors.Line,
                        focusedLeadingIconColor = MarketColors.Primary,
                        unfocusedLeadingIconColor = MarketColors.InkMuted,
                        focusedContainerColor = MarketColors.Paper,
                        unfocusedContainerColor = MarketColors.Paper,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedCategory?.displayName ?: "전체 문서",
                        style = MarketType.heading.copy(fontWeight = FontWeight.SemiBold),
                        color = MarketColors.Ink,
                    )
                }
            }
            LedgerDivider()
            if (articles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("일치하는 문서가 없습니다.", style = MarketType.heading, color = MarketColors.Ink)
                        Text("검색어나 분류를 바꿔보세요.", style = MarketType.body, color = MarketColors.InkMuted)
                    }
                }
            } else {
                VisibleVerticalScrollbar(state = listState, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                        items(articles, key = DictionaryArticle::id) { article ->
                            DictionaryArticleRow(article = article, onClick = { onArticleSelect(article) })
                            LedgerDivider(Modifier.padding(horizontal = MarketSpacing.md))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryArticleRow(article: DictionaryArticle, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MarketSpacing.md, vertical = MarketSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarketSpacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarketSpacing.xxs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarketSpacing.sm),
            ) {
                Text(
                    text = article.category.displayName,
                    modifier = Modifier
                        .background(MarketColors.PrimaryWeak, RoundedCornerShape(MarketRadii.pill))
                        .padding(horizontal = MarketSpacing.sm, vertical = MarketSpacing.xxs),
                    style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.PrimaryText,
                )
                Text(
                    text = article.title,
                    modifier = Modifier.weight(1f),
                    style = MarketType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = article.summary,
                style = MarketType.label,
                color = MarketColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (article.tags.isNotEmpty()) {
                Text(
                    text = article.tags.take(4).joinToString("  ·  "),
                    style = MarketType.caption,
                    color = MarketColors.Grey400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Lucide.ArrowRight,
            contentDescription = "${article.title} 열기",
            tint = MarketColors.Grey400,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun DictionaryArticle.matches(normalizedQuery: String): Boolean =
    title.lowercase().contains(normalizedQuery) ||
        summary.lowercase().contains(normalizedQuery) ||
        category.displayName.lowercase().contains(normalizedQuery) ||
        tags.any { it.lowercase().contains(normalizedQuery) }
