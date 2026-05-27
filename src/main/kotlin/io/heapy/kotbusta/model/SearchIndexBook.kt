package io.heapy.kotbusta.model

data class SearchIndexBook(
    val bookId: Int,
    val title: String,
    val authors: List<String>,
    val series: String?,
    val language: String,
    val genres: List<String>,
)
