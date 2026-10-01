package com.example.model

import com.example.parser.M3uItem

object PlayerRepository {
    var currentlyPlayingItem: M3uItem? = null
    var currentPlaylist: List<M3uItem> = emptyList()
    var isFavoritesPlaylist: Boolean = false
}
