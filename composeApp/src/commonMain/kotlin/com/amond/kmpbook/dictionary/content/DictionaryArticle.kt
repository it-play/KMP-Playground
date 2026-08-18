package com.amond.kmpbook.dictionary.content

data class DictionaryArticle(
    val id: String,
    val title: String,
    val summary: String,
    val category: DictionaryCategory,
    val tags: List<String>,
    val order: Int,
    val blocks: List<DictionaryContentBlock>,
    val sourcePath: String,
)
