with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

# 1. Update ImdbUpcomingItem
old_dataclass = """data class ImdbUpcomingItem(
    val id: String,
    val title: String,
    val type: String,
    val imdbRating: String,
    val posterUrl: String,
    val releaseDate: String,
    val timeFrame: ImdbTimeFrame,
    val genres: List<String>,
    val overview: String
)"""

new_dataclass = """data class ImdbUpcomingItem(
    val id: String,
    val title: String,
    val type: String,
    val imdbRating: String,
    val posterUrl: String,
    val releaseDate: String,
    val timeFrame: ImdbTimeFrame,
    val genres: List<String>,
    val overview: String,
    val tmdbId: String = "",
    val trailerKey: String? = null
)"""

content = content.replace(old_dataclass, new_dataclass)

# 2. Add fetchTrailerKey function
trailer_func = """
private fun fetchTrailerKey(tmdbId: String): String? {
    try {
        val apiKey = "15745c1c46c264d1170db38ea66049e0"
        val url = "https://api.themoviedb.org/3/movie/$tmdbId/videos?api_key=$apiKey"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        if (conn.responseCode == 200) {
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(json)
            val results = root.optJSONArray("results")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val video = results.optJSONObject(i)
                    if (video != null && video.optString("site").equals("YouTube", ignoreCase = true) && video.optString("type").equals("Trailer", ignoreCase = true)) {
                        return video.optString("key")
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
"""

# Insert fetchTrailerKey before parseTmdbList
content = content.replace("private fun parseTmdbList(", trailer_func + "\nprivate fun parseTmdbList(")

# 3. Update parseTmdbList to use fetchTrailerKey
old_parse_add = """            list.add(
                ImdbUpcomingItem(
                    id = "tmdb_${timeFrame.name}_$id",
                    title = title,
                    type = typeStr,
                    imdbRating = ratingStr,
                    posterUrl = "https://image.tmdb.org/t/p/w500$posterPath",
                    releaseDate = releaseDate,
                    timeFrame = timeFrame,
                    genres = genresList,
                    overview = if (overview.isNotBlank()) overview else "$title TMDB detayları."
                )
            )"""

new_parse_add = """            val trailerKey = fetchTrailerKey(id)
            list.add(
                ImdbUpcomingItem(
                    id = "tmdb_${timeFrame.name}_$id",
                    title = title,
                    type = typeStr,
                    imdbRating = ratingStr,
                    posterUrl = "https://image.tmdb.org/t/p/w500$posterPath",
                    releaseDate = releaseDate,
                    timeFrame = timeFrame,
                    genres = genresList,
                    overview = if (overview.isNotBlank()) overview else "$title TMDB detayları.",
                    tmdbId = id,
                    trailerKey = trailerKey
                )
            )"""

content = content.replace(old_parse_add, new_parse_add)

# 4. In ImdbCard, if trailerKey is not null, put a play icon on the poster. 
old_imdb_card_image = """                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )"""

new_imdb_card_image = """                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )
                if (item.trailerKey != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Fragman",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }"""

content = content.replace(old_imdb_card_image, new_imdb_card_image)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)

