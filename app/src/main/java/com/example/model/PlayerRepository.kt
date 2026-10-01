package com.example.model

import com.example.parser.M3uItem
import java.util.concurrent.ConcurrentHashMap

enum class ResumeChoice {
    RESUME,
    RESTART
}

object PlayerRepository {
    var currentlyPlayingItem: M3uItem? = null
    var currentPlaylist: List<M3uItem> = emptyList()
    var isFavoritesPlaylist: Boolean = false
    var preselectedResumeChoice: ResumeChoice? = null

    // Instant in-memory playback position cache: url -> Pair(positionMs, durationMs)
    val lastPositions = ConcurrentHashMap<String, Pair<Long, Long>>()

    // Dashboard navigation state preservation across player entry/exit
    var lastDashboardTabIndex: Int = 0
    val lastDashboardGroupByTab = ConcurrentHashMap<Int, String>()
    var lastDashboardFavoriteFilter: Int = 0
    var lastDashboardSearchQuery: String = ""
    var lastSelectedSeries: List<M3uItem>? = null

    fun getSavedPositionMs(url: String): Long {
        val pair = lastPositions[url] ?: return 0L
        val (pos, dur) = pair
        val isNearEnd = dur > 30_000L && pos >= dur - 8_000L
        return if (pos > 1000L && !isNearEnd) pos else 0L
    }
}
