import re

with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'r') as f:
    content = f.read()

# 1. Update ImdbTimeFrame Enum
content = content.replace(
'''enum class ImdbTimeFrame(val label: String) {
    ALL("Tümü"),
    WEEKLY("Haftalık Vizyon"),
    MONTHLY("Aylık Vizyon")
}''',
'''enum class ImdbTimeFrame(val label: String) {
    ALL("Tümü"),
    THIRTY_DAYS("30 Günlük"),
    SIXTY_DAYS("60 Günlük")
}'''
)

# 2. Update DashboardScreen local states for TMDb
content = content.replace(
    "var tmdbWeeklyItems by remember { mutableStateOf<List<ImdbUpcomingItem>>(emptyList()) }",
    "var tmdb30DaysItems by remember { mutableStateOf<List<ImdbUpcomingItem>>(emptyList()) }"
)
content = content.replace(
    "var tmdbMonthlyItems by remember { mutableStateOf<List<ImdbUpcomingItem>>(emptyList()) }",
    "var tmdb60DaysItems by remember { mutableStateOf<List<ImdbUpcomingItem>>(emptyList()) }"
)

# 3. Replace the LaunchedEffect block for TMDb
tmdb_launched_effect_old = '''    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val apiKey = "15745c1c46c264d1170db38ea66049e0"
                val weeklyUrl = "https://api.themoviedb.org/3/trending/all/week?api_key=$apiKey&language=tr-TR"
                val weeklyConn = java.net.URL(weeklyUrl).openConnection() as java.net.HttpURLConnection
                weeklyConn.connectTimeout = 8000
                weeklyConn.readTimeout = 8000
                if (weeklyConn.responseCode == 200) {
                    val weeklyJson = weeklyConn.inputStream.bufferedReader().use { it.readText() }
                    val weeklyParsed = parseTmdbList(weeklyJson, ImdbTimeFrame.WEEKLY)
                    if (weeklyParsed.isNotEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            tmdbWeeklyItems = weeklyParsed
                        }
                    }
                }

                val monthlyUrl = "https://api.themoviedb.org/3/movie/upcoming?api_key=$apiKey&language=tr-TR"
                val monthlyConn = java.net.URL(monthlyUrl).openConnection() as java.net.HttpURLConnection
                monthlyConn.connectTimeout = 8000
                monthlyConn.readTimeout = 8000
                if (monthlyConn.responseCode == 200) {
                    val monthlyJson = monthlyConn.inputStream.bufferedReader().use { it.readText() }
                    val monthlyParsed = parseTmdbList(monthlyJson, ImdbTimeFrame.MONTHLY)
                    if (monthlyParsed.isNotEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            tmdbMonthlyItems = monthlyParsed
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }'''

tmdb_launched_effect_new = '''    var liveFixtures by remember { mutableStateOf<List<MatchFixture>>(emptyList()) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val apiKey = "15745c1c46c264d1170db38ea66049e0"
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val cal = java.util.Calendar.getInstance()
                val dateToday = sdf.format(cal.time)
                cal.add(java.util.Calendar.DAY_OF_YEAR, 30)
                val date30 = sdf.format(cal.time)
                cal.add(java.util.Calendar.DAY_OF_YEAR, 30)
                val date60 = sdf.format(cal.time)

                val url30 = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=tr-TR&primary_release_date.gte=$dateToday&primary_release_date.lte=$date30&sort_by=popularity.desc"
                val conn30 = java.net.URL(url30).openConnection() as java.net.HttpURLConnection
                conn30.connectTimeout = 8000
                conn30.readTimeout = 8000
                if (conn30.responseCode == 200) {
                    val json30 = conn30.inputStream.bufferedReader().use { it.readText() }
                    val parsed30 = parseTmdbList(json30, ImdbTimeFrame.THIRTY_DAYS)
                    if (parsed30.isNotEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            tmdb30DaysItems = parsed30
                        }
                    }
                }

                val url60 = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=tr-TR&primary_release_date.gte=$dateToday&primary_release_date.lte=$date60&sort_by=popularity.desc"
                val conn60 = java.net.URL(url60).openConnection() as java.net.HttpURLConnection
                conn60.connectTimeout = 8000
                conn60.readTimeout = 8000
                if (conn60.responseCode == 200) {
                    val json60 = conn60.inputStream.bufferedReader().use { it.readText() }
                    val parsed60 = parseTmdbList(json60, ImdbTimeFrame.SIXTY_DAYS)
                    if (parsed60.isNotEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            tmdb60DaysItems = parsed60
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Fetch live sports fixtures from ESPN
            try {
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -2)
                val sdfEspn = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                val dateStart = sdfEspn.format(cal.time)
                cal.add(java.util.Calendar.DAY_OF_YEAR, 30)
                val dateEnd = sdfEspn.format(cal.time)
                
                val espnUrl = "https://site.api.espn.com/apis/site/v2/sports/soccer/tur.1/scoreboard?dates=$dateStart-$dateEnd&limit=100"
                val espnConn = java.net.URL(espnUrl).openConnection() as java.net.HttpURLConnection
                espnConn.connectTimeout = 8000
                espnConn.readTimeout = 8000
                if (espnConn.responseCode == 200) {
                    val espnJson = espnConn.inputStream.bufferedReader().use { it.readText() }
                    val parsedFixtures = parseEspnFixtures(espnJson)
                    if (parsedFixtures.isNotEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            liveFixtures = parsedFixtures
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }'''
content = content.replace(tmdb_launched_effect_old, tmdb_launched_effect_new)

# 4. Update imdbReleases merging logic
content = content.replace(
    "val imdbReleases = remember(tmdbWeeklyItems, tmdbMonthlyItems, defaultImdbReleases) {",
    "val imdbReleases = remember(tmdb30DaysItems, tmdb60DaysItems, defaultImdbReleases) {"
)
content = content.replace(
    "if (tmdbWeeklyItems.isNotEmpty() || tmdbMonthlyItems.isNotEmpty()) {",
    "if (tmdb30DaysItems.isNotEmpty() || tmdb60DaysItems.isNotEmpty()) {"
)
content = content.replace(
    "tmdbWeeklyItems + tmdbMonthlyItems",
    "tmdb30DaysItems + tmdb60DaysItems"
)

# 5. Remove hardcoded fixtures entirely and use liveFixtures
fixtures_old = '''    val fixtures = remember {
        listOf(
            MatchFixture(
                id = "m1",
                homeTeam = "Fenerbahçe",
                awayTeam = "Galatasaray",
                homeLogo = "https://media.api-sports.io/football/teams/611.png",
                awayLogo = "https://media.api-sports.io/football/teams/600.png",
                league = "Trendyol Süper Lig • Dev Derbi",
                dateText = "Bugün 20:00",
                isLive = true,
                channelKeywords = listOf("BeIN Sports 1", "BeIN 1", "bein", "Spor")
            ),
            MatchFixture(
                id = "m2",
                homeTeam = "Beşiktaş",
                awayTeam = "Trabzonspor",
                homeLogo = "https://media.api-sports.io/football/teams/603.png",
                awayLogo = "https://media.api-sports.io/football/teams/1010.png",
                league = "Trendyol Süper Lig",
                dateText = "Yarın 19:00",
                isLive = false,
                channelKeywords = listOf("BeIN Sports 1", "BeIN Sports 2", "BeIN", "Spor")
            ),
            MatchFixture(
                id = "m3",
                homeTeam = "Fenerbahçe",
                awayTeam = "Lille",
                homeLogo = "https://media.api-sports.io/football/teams/611.png",
                awayLogo = "https://media.api-sports.io/football/teams/79.png",
                league = "UEFA Şampiyonlar Ligi",
                dateText = "Salı 21:00",
                isLive = false,
                channelKeywords = listOf("Exxen", "S Sport", "TRT Spor", "TRT 1")
            ),
            MatchFixture(
                id = "m4",
                homeTeam = "Galatasaray",
                awayTeam = "Young Boys",
                homeLogo = "https://media.api-sports.io/football/teams/600.png",
                awayLogo = "https://media.api-sports.io/football/teams/552.png",
                league = "UEFA Şampiyonlar Ligi",
                dateText = "Çarşamba 22:00",
                isLive = false,
                channelKeywords = listOf("TRT 1", "TRT Spor", "Exxen", "BeIN")
            )
        )
    }'''

content = content.replace(fixtures_old, "val fixtures = liveFixtures")

# Add parseEspnFixtures function
import_json = "import org.json.JSONObject"
if "import org.json.JSONObject" not in content:
    content = content.replace("import org.json.JSONArray", "import org.json.JSONObject\nimport org.json.JSONArray")

parse_espn_fn = '''private fun parseEspnFixtures(jsonString: String): List<MatchFixture> {
    val list = mutableListOf<MatchFixture>()
    try {
        val root = org.json.JSONObject(jsonString)
        val events = root.optJSONArray("events") ?: return emptyList()
        val targetTeams = listOf("Fenerbahce", "Galatasaray", "Besiktas", "Trabzonspor", "Fenerbahçe", "Beşiktaş")
        
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val competitions = event.optJSONArray("competitions") ?: continue
            if (competitions.length() == 0) continue
            val comp = competitions.optJSONObject(0) ?: continue
            val competitors = comp.optJSONArray("competitors") ?: continue
            if (competitors.length() < 2) continue
            
            var homeTeam = ""
            var awayTeam = ""
            var homeLogo = ""
            var awayLogo = ""
            var homeScore = ""
            var awayScore = ""
            
            for (j in 0 until competitors.length()) {
                val c = competitors.optJSONObject(j) ?: continue
                val isHome = c.optString("homeAway") == "home"
                val team = c.optJSONObject("team") ?: continue
                val name = team.optString("name")
                val logo = team.optString("logo")
                val score = c.optString("score", "")
                
                if (isHome) {
                    homeTeam = name
                    homeLogo = logo
                    homeScore = score
                } else {
                    awayTeam = name
                    awayLogo = logo
                    awayScore = score
                }
            }
            
            val isTargetMatch = targetTeams.any { homeTeam.contains(it, ignoreCase = true) || awayTeam.contains(it, ignoreCase = true) }
            if (!isTargetMatch) continue
            
            // Format date
            val dateStr = event.optString("date") // e.g. 2024-08-14T18:30Z
            var dateText = dateStr
            try {
                val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", java.util.Locale.US)
                sdfIn.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val dateObj = sdfIn.parse(dateStr)
                if (dateObj != null) {
                    val sdfOut = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale("tr"))
                    dateText = sdfOut.format(dateObj)
                }
            } catch (e: Exception) {}
            
            val status = event.optJSONObject("status")
            val type = status?.optJSONObject("type")
            val state = type?.optString("state")
            val isLive = state == "in"
            val isCompleted = state == "post"
            
            if (isCompleted) continue // Skip past matches for upcoming fixtures, or keep them? Let's keep if we want, but usually fixtures means upcoming/live.
            
            var displayDate = dateText
            if (isLive) {
                val detail = type?.optString("detail")
                displayDate = if (!detail.isNullOrEmpty()) "CANLI - $detail" else "CANLI"
            }
            
            val displayLeague = "Trendyol Süper Lig"
            
            list.add(
                MatchFixture(
                    id = event.optString("id", java.util.UUID.randomUUID().toString()),
                    homeTeam = homeTeam,
                    awayTeam = awayTeam,
                    homeLogo = homeLogo,
                    awayLogo = awayLogo,
                    league = displayLeague,
                    dateText = displayDate,
                    isLive = isLive,
                    channelKeywords = listOf("BeIN Sports 1", "BeIN 1", "bein", "Spor")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}
'''

if "fun parseEspnFixtures" not in content:
    content += "\n" + parse_espn_fn

with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'w') as f:
    f.write(content)
