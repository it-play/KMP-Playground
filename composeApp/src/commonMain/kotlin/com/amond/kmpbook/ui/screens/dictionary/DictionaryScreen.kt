package com.amond.kmpbook.ui.screens.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.amond.kmpbook.dictionary.content.DictionaryArticle
import com.amond.kmpbook.dictionary.content.DictionaryCategory
import com.amond.kmpbook.dictionary.content.DictionaryContentRepository
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType
import kotlinx.coroutines.CancellationException

@Composable
fun DictionaryScreen(modifier: Modifier = Modifier) {
    val repository = remember { DictionaryContentRepository() }
    var loadAttempt by remember { mutableIntStateOf(0) }
    val articlesResult by produceState<Result<List<DictionaryArticle>>?>(
        initialValue = null,
        repository,
        loadAttempt,
    ) {
        value = null
        value = try {
            Result.success(repository.loadArticles())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
    var selectedArticleId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<DictionaryCategory?>(null) }

    Box(
        modifier = modifier.fillMaxSize().padding(MarketLayout.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        when {
            articlesResult == null -> CircularProgressIndicator(color = MarketColors.Primary)
            articlesResult?.isFailure == true -> DictionaryLoadFailure(
                message = articlesResult?.exceptionOrNull()?.message ?: "사전 콘텐츠를 읽지 못했습니다.",
                onRetry = {
                    loadAttempt += 1
                },
            )
            else -> {
                val articles = articlesResult?.getOrDefault(emptyList()).orEmpty()
                val selectedArticle = selectedArticleId?.let { id -> articles.firstOrNull { it.id == id } }
                if (selectedArticle == null) {
                    DictionaryHome(
                        articles = articles,
                        query = query,
                        selectedCategory = selectedCategory,
                        onQueryChange = { query = it },
                        onCategorySelect = { selectedCategory = it },
                        onArticleSelect = { selectedArticleId = it.id },
                    )
                } else {
                    DictionaryArticleReader(
                        article = selectedArticle,
                        onBack = { selectedArticleId = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryLoadFailure(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MarketSpacing.md),
    ) {
        Text(
            text = "사전을 열 수 없습니다",
            style = MarketType.heading.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.Ink,
        )
        Text(text = message, style = MarketType.body, color = MarketColors.InkMuted)
        MarketButton(text = "다시 읽기", onClick = onRetry)
    }
}
