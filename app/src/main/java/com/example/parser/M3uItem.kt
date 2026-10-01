package com.example.parser

enum class ItemType {
    LIVE, MOVIE, SERIES
}

data class M3uItem(
    val title: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val type: ItemType = ItemType.LIVE,
    val seriesName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val tvgId: String? = null,
    val tvgName: String? = null
)
