package com.amond.kmpbook.dictionary.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kmpbook.composeapp.generated.resources.Res

class DictionaryContentRepository(
    private val parser: DictionaryMdxParser = DictionaryMdxParser(),
) {
    private val cacheMutex = Mutex()
    private var cachedArticles: List<DictionaryArticle>? = null

    suspend fun loadArticles(): List<DictionaryArticle> = cacheMutex.withLock {
        cachedArticles ?: loadUncachedArticles().also { cachedArticles = it }
    }

    private suspend fun loadUncachedArticles(): List<DictionaryArticle> {
        val indexSource = readResourceText(INDEX_RESOURCE_PATH, "사전 색인")
        val articlePaths = parseArticlePaths(indexSource)
        val articles = articlePaths.map { sourcePath ->
            parser.parse(
                sourcePath = sourcePath,
                source = readResourceText(sourcePath, "사전 문서"),
            )
        }
        val duplicateIds = articles
            .groupingBy { it.id }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            throw DictionaryContentException(
                "사전 문서 id가 중복되었습니다: ${duplicateIds.sorted().joinToString()}",
            )
        }

        return articles.sortedWith(
            compareBy<DictionaryArticle> { it.category.indexCode }
                .thenBy { it.order }
                .thenBy { it.title }
                .thenBy { it.id },
        )
    }

    private fun parseArticlePaths(source: String): List<String> {
        val root = try {
            JSON.parseToJsonElement(source) as? JsonObject
                ?: throw DictionaryContentException("사전 색인 '$INDEX_RESOURCE_PATH'의 최상위 값은 객체여야 합니다.")
        } catch (error: DictionaryContentException) {
            throw error
        } catch (error: Throwable) {
            throw DictionaryContentException(
                "사전 색인 '$INDEX_RESOURCE_PATH'의 JSON 형식이 올바르지 않습니다: " +
                    (error.message ?: "원인을 확인할 수 없습니다."),
                error,
            )
        }
        val articlesElement = root[ARTICLES_KEY]
            ?: throw DictionaryContentException(
                "사전 색인 '$INDEX_RESOURCE_PATH'에 '$ARTICLES_KEY' 배열이 없습니다.",
            )
        val articlesArray = articlesElement as? JsonArray
            ?: throw DictionaryContentException(
                "사전 색인 '$INDEX_RESOURCE_PATH'의 '$ARTICLES_KEY'는 문자열 배열이어야 합니다.",
            )
        if (articlesArray.isEmpty()) {
            throw DictionaryContentException("사전 색인 '$INDEX_RESOURCE_PATH'에 문서가 없습니다.")
        }

        val paths = articlesArray.mapIndexed { index, element ->
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                throw DictionaryContentException(
                    "사전 색인 '$INDEX_RESOURCE_PATH'의 articles[$index]는 문자열이어야 합니다.",
                )
            }
            primitive.content.trim().also { path -> validateArticlePath(index, path) }
        }
        val duplicatePaths = paths.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicatePaths.isNotEmpty()) {
            throw DictionaryContentException(
                "사전 색인 '$INDEX_RESOURCE_PATH'에 중복 문서 경로가 있습니다: " +
                    duplicatePaths.sorted().joinToString(),
            )
        }
        return paths
    }

    private fun validateArticlePath(
        index: Int,
        path: String,
    ) {
        val isValid = path.startsWith(ARTICLE_RESOURCE_DIRECTORY) &&
            path.endsWith(MDX_EXTENSION) &&
            ".." !in path &&
            '\\' !in path
        if (!isValid) {
            throw DictionaryContentException(
                "사전 색인 '$INDEX_RESOURCE_PATH'의 articles[$index] 경로가 올바르지 않습니다: '$path'. " +
                    "'$ARTICLE_RESOURCE_DIRECTORY' 아래의 .mdx 파일만 사용할 수 있습니다.",
            )
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun readResourceText(
        resourcePath: String,
        resourceLabel: String,
    ): String = try {
        Res.readBytes(resourcePath).decodeToString()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        throw DictionaryContentException(
            "$resourceLabel '$resourcePath'을 읽지 못했습니다: " +
                (error.message ?: "원인을 확인할 수 없습니다."),
            error,
        )
    }

    private companion object {
        const val INDEX_RESOURCE_PATH = "files/dictionary/index.json"
        const val ARTICLE_RESOURCE_DIRECTORY = "files/dictionary/articles/"
        const val ARTICLES_KEY = "articles"
        const val MDX_EXTENSION = ".mdx"
        val JSON = Json
    }
}
