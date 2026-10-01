package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.res.Configuration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.findActivity
import com.example.model.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.model.SupportRepository
import com.example.model.db.PlaybackProgressEntity
import com.example.parser.ItemType
import com.example.parser.M3uItem
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onPlayStream: (M3uItem) -> Unit,
    onNavigateToAuth: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToDeviceInfo: () -> Unit = {}
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val playWithPlaylist: (M3uItem, List<M3uItem>) -> Unit = { item, playlist ->
        com.example.model.PlayerRepository.currentPlaylist = playlist
        com.example.model.PlayerRepository.isFavoritesPlaylist = (selectedTabIndex == 4 && item.type != ItemType.SERIES)
        onPlayStream(item)
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val orientationMode by com.example.model.SettingsManager.orientationMode.collectAsState()
    val isPortrait = orientationMode == 1 ||
        configuration.orientation == Configuration.ORIENTATION_PORTRAIT ||
        configuration.screenWidthDp < configuration.screenHeightDp ||
        configuration.screenWidthDp < 560
    val isLandscape = !isPortrait
    val currentLang by com.example.model.AppLanguageManager.currentLanguage.collectAsState()
    val allItems by PlaylistRepository.playlist.collectAsState()
    val hiddenCategories by com.example.model.CategoryManager.hiddenCategories.collectAsState()
    val visibleItems = remember(allItems, hiddenCategories) {
        allItems.filter { !hiddenCategories.contains(it.group) }
    }
    val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
    val db = remember { com.example.model.db.AppDatabase.getDatabase(context) }
    val recentMovies by db.iptvDao().getRecentProgressForType("MOVIE").collectAsState(initial = emptyList())
    val recentSeries by db.iptvDao().getRecentProgressForType("SERIES").collectAsState(initial = emptyList())
    val recentAll: List<PlaybackProgressEntity> by db.iptvDao().getAllRecentProgress().collectAsState(initial = emptyList())

    // Tabs: 0: Home (Anasayfa), 1: Live (Canlı), 2: Movies (Flim), 3: Series (Dizi), 4: Favorites (Favori)
    val tabs = listOf(
        stringResource(R.string.nav_home),
        stringResource(R.string.tab_live),
        stringResource(R.string.tab_movies),
        stringResource(R.string.tab_series),
        stringResource(R.string.nav_favorites)
    )
    val tabIcons = listOf(
        Icons.Default.Home,
        Icons.Default.LiveTv,
        Icons.Default.Movie,
        Icons.Default.Tv,
        Icons.Default.Favorite
    )

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showCategoryDrawer by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                com.example.model.SupportRepository.syncTicketsFromOdoo()
            } catch (e: Exception) {}
            try {
                com.example.model.UpdateManager.checkForUpdates(context, manual = false)
            } catch (e: Exception) {}
            try {
                val targetUserId = com.example.model.DeviceManager.getCurrentUserId()
                    ?: com.example.model.DeviceManager.getCurrentUserEmail()
                    ?: com.example.model.DeviceManager.getDeviceId()
                com.example.model.OdooIntegrationManager.sendDeviceToPortalAndSyncAll(
                    context = context,
                    userId = targetUserId,
                    userName = com.example.model.DeviceManager.getCurrentUserName() ?: "",
                    userEmail = com.example.model.DeviceManager.getCurrentUserEmail() ?: ""
                )
            } catch (e: Exception) {}
        }
        while (true) {
            kotlinx.coroutines.delay(25000)
            try {
                val targetUserId = com.example.model.DeviceManager.getCurrentUserId()
                    ?: com.example.model.DeviceManager.getCurrentUserEmail()
                    ?: com.example.model.DeviceManager.getDeviceId()
                com.example.model.OdooIntegrationManager.syncPlaylistsFromOdoo(context, targetUserId)
            } catch (e: Exception) {}
        }
    }

    // Navigation and Sheets state
    var selectedSeries by remember { mutableStateOf<List<M3uItem>?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }
    var showAllFixturesSheet by remember { mutableStateOf(false) }
    var showProUpgradeDialog by remember { mutableStateOf(false) }

    var itemToUnlock by remember { mutableStateOf<M3uItem?>(null) }
    var playlistForUnlock by remember { mutableStateOf<List<M3uItem>?>(null) }
    var seriesToUnlock by remember { mutableStateOf<List<M3uItem>?>(null) }

    // TMDb and ESPN state
    var tmdbWeeklyItems by remember { mutableStateOf<List<ImdbUpcomingItem>>(emptyList()) }
    var tmdb30DaysItems by remember { mutableStateOf<List<ImdbUpcomingItem>>(emptyList()) }
    var tmdb60DaysItems by remember { mutableStateOf<List<ImdbUpcomingItem>>(emptyList()) }
    var liveFixtures by remember { mutableStateOf<List<MatchFixture>>(getDefaultFixtures()) }
    var selectedImdbTimeFrame by remember { mutableStateOf(ImdbTimeFrame.ALL) }
    var selectedImdbItem by remember { mutableStateOf<ImdbUpcomingItem?>(null) }
    var notifiedTmdbIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isFixturesRefreshing by remember { mutableStateOf(false) }
    var isMediaDataRefreshing by remember { mutableStateOf(false) }

    val refreshFixtures: (Boolean) -> Unit = { manual ->
        scope.launch {
            isFixturesRefreshing = true
            withContext(Dispatchers.IO) {
                try {
                    val calEspn = Calendar.getInstance()
                    calEspn.add(Calendar.DAY_OF_YEAR, -2)
                    val sdfEspn = SimpleDateFormat("yyyyMMdd", Locale.US)
                    val dateStart = sdfEspn.format(calEspn.time)
                    calEspn.add(Calendar.DAY_OF_YEAR, 30)
                    val dateEnd = sdfEspn.format(calEspn.time)

                    val leaguesToFetch = listOf(
                        "tur.1" to "Trendyol Süper Lig",
                        "uefa.champions" to "UEFA Şampiyonlar Ligi",
                        "uefa.europa" to "UEFA Avrupa Ligi"
                    )

                    val allFetched = mutableListOf<MatchFixture>()
                    for ((leagueCode, leagueName) in leaguesToFetch) {
                        try {
                            val espnUrl = "https://site.api.espn.com/apis/site/v2/sports/soccer/$leagueCode/scoreboard?dates=$dateStart-$dateEnd&limit=100"
                            val espnConn = URL(espnUrl).openConnection() as HttpURLConnection
                            espnConn.connectTimeout = 7000
                            espnConn.readTimeout = 7000
                            if (espnConn.responseCode == 200) {
                                val espnJson = espnConn.inputStream.bufferedReader().use { it.readText() }
                                val parsed = parseEspnFixtures(espnJson, leagueName)
                                allFetched.addAll(parsed)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    val combined = getCombinedFixtures(allFetched)
                    if (combined.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            liveFixtures = combined
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        isFixturesRefreshing = false
                        if (manual) {
                            Toast.makeText(context, "Günün maçları ve fikstür güncellendi.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    val refreshMediaData: (Boolean) -> Unit = { manual ->
        scope.launch {
            isMediaDataRefreshing = true
            withContext(Dispatchers.IO) {
                try {
                    val apiKey = "15745c1c46c264d1170db38ea66049e0"
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val cal = Calendar.getInstance()
                    val dateToday = sdf.format(cal.time)
                    cal.add(Calendar.DAY_OF_YEAR, 30)
                    val date30 = sdf.format(cal.time)
                    cal.add(Calendar.DAY_OF_YEAR, 30)
                    val date60 = sdf.format(cal.time)

                    // 1. Weekly Trending (Movies & TV Series)
                    try {
                        val urlWeekly = "https://api.themoviedb.org/3/trending/all/week?api_key=$apiKey&language=tr-TR"
                        val connWeekly = URL(urlWeekly).openConnection() as HttpURLConnection
                        connWeekly.connectTimeout = 7000
                        connWeekly.readTimeout = 7000
                        if (connWeekly.responseCode == 200) {
                            val jsonWeekly = connWeekly.inputStream.bufferedReader().use { it.readText() }
                            val parsedWeekly = parseTmdbList(jsonWeekly, ImdbTimeFrame.WEEKLY, context)
                            if (parsedWeekly.isNotEmpty()) {
                                withContext(Dispatchers.Main) { tmdbWeeklyItems = parsedWeekly }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 2. 30 Days Upcoming (Monthly)
                    try {
                        val url30 = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=tr-TR&primary_release_date.gte=$dateToday&primary_release_date.lte=$date30&sort_by=popularity.desc"
                        val conn30 = URL(url30).openConnection() as HttpURLConnection
                        conn30.connectTimeout = 7000
                        conn30.readTimeout = 7000
                        if (conn30.responseCode == 200) {
                            val json30 = conn30.inputStream.bufferedReader().use { it.readText() }
                            val parsed30 = parseTmdbList(json30, ImdbTimeFrame.THIRTY_DAYS, context)
                            if (parsed30.isNotEmpty()) {
                                withContext(Dispatchers.Main) { tmdb30DaysItems = parsed30 }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 3. 60 Days Upcoming
                    try {
                        val url60 = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=tr-TR&primary_release_date.gte=$dateToday&primary_release_date.lte=$date60&sort_by=popularity.desc"
                        val conn60 = URL(url60).openConnection() as HttpURLConnection
                        conn60.connectTimeout = 7000
                        conn60.readTimeout = 7000
                        if (conn60.responseCode == 200) {
                            val json60 = conn60.inputStream.bufferedReader().use { it.readText() }
                            val parsed60 = parseTmdbList(json60, ImdbTimeFrame.SIXTY_DAYS, context)
                            if (parsed60.isNotEmpty()) {
                                withContext(Dispatchers.Main) { tmdb60DaysItems = parsed60 }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        isMediaDataRefreshing = false
                        if (manual) {
                            Toast.makeText(context, "Haftalık ve aylık film/dizi verileri güncellendi.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Automatic Fetch on launch
    LaunchedEffect(Unit) {
        refreshFixtures(false)
        refreshMediaData(false)
    }

    // Automatic Fetch when navigating to tabs if empty
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex in 2..3 && tmdbWeeklyItems.isEmpty()) {
            refreshMediaData(false)
        }
    }

    val currentType: ItemType? = remember(selectedTabIndex) {
        when (selectedTabIndex) {
            1 -> ItemType.LIVE
            2 -> ItemType.MOVIE
            3 -> ItemType.SERIES
            else -> null
        }
    }

    val displayGroups = remember(visibleItems, currentType, selectedTabIndex, favoriteUrls) {
        when {
            currentType != null -> PlaylistRepository.getGroups(currentType)
            selectedTabIndex == 4 -> visibleItems
                .filter { favoriteUrls.contains(it.url) }
                .mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }
                .distinct()
                .sorted()
            else -> emptyList()
        }
    }
    var selectedGroup by remember(selectedTabIndex) { mutableStateOf<String?>(null) }
    var selectedFavoriteFilter by remember(selectedTabIndex) { mutableStateOf(0) } // 0: Tümü, 1: Filmler, 2: Diziler, 3: Canlı TV'ler
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = true) {
        when {
            showMenu -> showMenu = false
            showCategoryDrawer -> showCategoryDrawer = false
            selectedSeries != null -> selectedSeries = null
            showSettingsSheet -> showSettingsSheet = false
            showProfileSheet -> showProfileSheet = false
            showSupportSheet -> showSupportSheet = false
            showAllFixturesSheet -> showAllFixturesSheet = false
            showProUpgradeDialog -> showProUpgradeDialog = false
            selectedImdbItem != null -> selectedImdbItem = null
            itemToUnlock != null -> {
                itemToUnlock = null
                playlistForUnlock = null
            }
            seriesToUnlock != null -> seriesToUnlock = null
            isSearchExpanded || searchQuery.isNotEmpty() -> {
                isSearchExpanded = false
                searchQuery = ""
            }
            selectedGroup != null -> selectedGroup = null
            selectedTabIndex != 0 -> selectedTabIndex = 0
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2500) {
                    context.findActivity()?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, context.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val currentTabItems = remember(visibleItems, selectedTabIndex, selectedGroup, searchQuery, favoriteUrls) {
        when (selectedTabIndex) {
            1 -> {
                PlaylistRepository.getItemsForGroup(selectedGroup, ItemType.LIVE).filter {
                    it.title.contains(searchQuery, ignoreCase = true)
                }
            }
            2 -> {
                PlaylistRepository.getItemsForGroup(selectedGroup, ItemType.MOVIE).filter {
                    it.title.contains(searchQuery, ignoreCase = true)
                }
            }
            3 -> {
                PlaylistRepository.getItemsForGroup(selectedGroup, ItemType.SERIES).filter {
                    it.title.contains(searchQuery, ignoreCase = true) || (it.seriesName?.contains(searchQuery, ignoreCase = true) == true)
                }
            }
            4 -> {
                visibleItems.filter {
                    favoriteUrls.contains(it.url) &&
                        (selectedGroup == null || it.group == selectedGroup) &&
                        (it.title.contains(searchQuery, ignoreCase = true) || (it.seriesName?.contains(searchQuery, ignoreCase = true) == true))
                }
            }
            else -> emptyList()
        }
    }

    val groupCounts = remember(visibleItems, currentType, selectedTabIndex, favoriteUrls) {
        when {
            currentType != null -> {
                visibleItems.filter { it.type == currentType }
                    .groupingBy { it.group ?: "" }
                    .eachCount()
            }
            selectedTabIndex == 4 -> {
                visibleItems.filter { favoriteUrls.contains(it.url) }
                    .groupingBy { it.group ?: "" }
                    .eachCount()
            }
            else -> emptyMap()
        }
    }
    val totalTypeCount = remember(visibleItems, currentType, selectedTabIndex, favoriteUrls) {
        when {
            currentType != null -> visibleItems.count { it.type == currentType }
            selectedTabIndex == 4 -> visibleItems.count { favoriteUrls.contains(it.url) }
            else -> 0
        }
    }

    val imdbReleases = remember(tmdbWeeklyItems, tmdb30DaysItems, tmdb60DaysItems, selectedImdbTimeFrame) {
        val all = tmdbWeeklyItems + tmdb30DaysItems + tmdb60DaysItems
        when (selectedImdbTimeFrame) {
            ImdbTimeFrame.ALL -> all.distinctBy { it.id }
            ImdbTimeFrame.WEEKLY -> tmdbWeeklyItems
            ImdbTimeFrame.THIRTY_DAYS -> tmdb30DaysItems
            ImdbTimeFrame.SIXTY_DAYS -> tmdb60DaysItems
        }
    }

    if (itemToUnlock != null) {
        com.example.ui.components.PinUnlockDialog(
            onUnlock = {
                val item = itemToUnlock
                val pl = playlistForUnlock
                itemToUnlock = null
                playlistForUnlock = null
                if (item != null) {
                    if (pl != null) {
                        playWithPlaylist(item, pl)
                    } else {
                        onPlayStream(item)
                    }
                }
            },
            onDismiss = {
                itemToUnlock = null
                playlistForUnlock = null
            }
        )
    }

    if (seriesToUnlock != null) {
        com.example.ui.components.PinUnlockDialog(
            onUnlock = {
                val s = seriesToUnlock
                seriesToUnlock = null
                if (s != null) selectedSeries = s
            },
            onDismiss = { seriesToUnlock = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(top = 15.dp, bottom = 15.dp)
        ) {
    val actionsMenuContent = @Composable {
                IconButton(onClick = { isSearchExpanded = !isSearchExpanded }, modifier = Modifier.size(42.dp)) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_placeholder),
                        tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_desc), tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .heightIn(max = if (isLandscape) 220.dp else 390.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_desc), fontSize = 13.sp) },
                            onClick = {
                                showProfileSheet = true
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(40.dp)
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.support_tickets_title), fontSize = 13.sp) },
                            onClick = { showSupportSheet = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(40.dp)
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlists_title), fontSize = 13.sp) },
                            onClick = { onNavigateToPlaylists(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Subscriptions, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(40.dp)
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.device_info_title), fontSize = 13.sp) },
                            onClick = { onNavigateToDeviceInfo(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(40.dp)
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_desc), fontSize = 13.sp) },
                            onClick = {
                                showSettingsSheet = true
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(40.dp)
                        )
                        if (!com.example.model.DeviceManager.isProPurchased()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_upgrade_package), color = Color(0xFFFFB300), fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                onClick = { showProUpgradeDialog = true; showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.height(40.dp)
                            )
                        }
                    }
                }
            }

            // Top App Bar / Navigation Header
            if (isPortrait) {
                // Portrait Mode: Top Bar with Logo & Title + Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedTabIndex = 0 }
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    actionsMenuContent()
                }

                // Portrait Mode: Full-width non-scrollable Tab Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141822))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTabIndex == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedTabIndex = index
                                    selectedGroup = null
                                    searchQuery = ""
                                }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color(0xFF1C2230).copy(alpha = 0.5f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    tabIcons[index],
                                    contentDescription = title,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFB0BEC5),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCFD8DC),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .height(2.5.dp)
                                        .width(if (isSelected) 28.dp else 0.dp)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            RoundedCornerShape(1.5.dp)
                                        )
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF1E2430), thickness = 1.dp)
            } else {
                // Landscape Mode: Single-row bar with Logo + Non-scrollable Tabs + Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 12.dp)
                            .clickable { selectedTabIndex = 0 }
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTabIndex == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        selectedTabIndex = index
                                        selectedGroup = null
                                        searchQuery = ""
                                    }
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        tabIcons[index],
                                        contentDescription = title,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFB0BEC5),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCFD8DC),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    actionsMenuContent()
                }
            }

            // Support Notification Banner
            val notification by SupportRepository.unreadNotification.collectAsState()
            if (notification != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable {
                            showSupportSheet = true
                            SupportRepository.dismissNotification()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.notification_banner_title), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.notification_banner_msg), color = Color.LightGray, fontSize = 10.sp)
                        }
                        TextButton(onClick = {
                            showSupportSheet = true
                            SupportRepository.dismissNotification()
                        }) {
                            Text(stringResource(R.string.view_ticket_btn), color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        IconButton(onClick = { SupportRepository.dismissNotification() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Search Bar, Favorite Sub-Category Tabs & Category Filter Trigger
            val favMoviesTotal = remember(currentTabItems) { currentTabItems.count { it.type == ItemType.MOVIE } }
            val favSeriesTotal = remember(currentTabItems) {
                currentTabItems.filter { it.type == ItemType.SERIES }
                    .groupBy { (it.seriesName ?: it.title).trim().trimEnd('-', '–', '—', ':', '|', ' ').trim() }
                    .size
            }
            val favLiveTotal = remember(currentTabItems) { currentTabItems.count { it.type == ItemType.LIVE } }
            val favTotalCount = favMoviesTotal + favSeriesTotal + favLiveTotal

            AnimatedVisibility(
                visible = isSearchExpanded ||
                    (selectedTabIndex == 4 && (currentTabItems.isNotEmpty() || selectedGroup != null || searchQuery.isNotEmpty())) ||
                    (selectedTabIndex in 1..3 && displayGroups.isNotEmpty())
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSearchExpanded) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_placeholder), tint = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(20.dp),
                                singleLine = true
                            )
                        } else if (selectedTabIndex == 4) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val favTabs = listOf(
                                    Triple(0, "${stringResource(R.string.timeframe_all)} ($favTotalCount)", Icons.Default.Favorite),
                                    Triple(1, "${stringResource(R.string.fav_section_movies)} ($favMoviesTotal)", Icons.Default.Movie),
                                    Triple(2, "${stringResource(R.string.fav_section_series)} ($favSeriesTotal)", Icons.Default.Tv),
                                    Triple(3, "${stringResource(R.string.fav_section_live)} ($favLiveTotal)", Icons.Default.LiveTv)
                                )
                                favTabs.forEach { (filterIdx, label, icon) ->
                                    val isFilterSelected = selectedFavoriteFilter == filterIdx
                                    val accentColor = when (filterIdx) {
                                        1 -> Color(0xFFE50914)
                                        2 -> Color(0xFFFFB300)
                                        3 -> Color(0xFF42A5F5)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isFilterSelected) accentColor.copy(alpha = 0.22f) else Color(0xFF1A1F2B),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isFilterSelected) accentColor else Color(0xFF2D3548)
                                        ),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .clickable { selectedFavoriteFilter = filterIdx }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isFilterSelected) accentColor else Color(0xFFB0BEC5),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = label,
                                                color = if (isFilterSelected) Color.White else Color(0xFFCFD8DC),
                                                fontSize = 12.sp,
                                                fontWeight = if (isFilterSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Quick Refresh Button based on selected tab
                        if (selectedTabIndex in 2..4) {
                            IconButton(
                                onClick = { refreshMediaData(true) },
                                enabled = !isMediaDataRefreshing,
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            ) {
                                if (isMediaDataRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Film/Dizi Verilerini Güncelle",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        if (selectedTabIndex in 1..4 && displayGroups.isNotEmpty()) {
                            IconButton(
                                onClick = { showCategoryDrawer = true },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (selectedGroup != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.categories),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    if (isSearchExpanded && selectedTabIndex == 4 && currentTabItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val favTabs = listOf(
                                Triple(0, "Tümü ($favTotalCount)", Icons.Default.Favorite),
                                Triple(1, "Filmler ($favMoviesTotal)", Icons.Default.Movie),
                                Triple(2, "Diziler ($favSeriesTotal)", Icons.Default.Tv),
                                Triple(3, "Canlı TV'ler ($favLiveTotal)", Icons.Default.LiveTv)
                            )
                            favTabs.forEach { (filterIdx, label, icon) ->
                                val isFilterSelected = selectedFavoriteFilter == filterIdx
                                val accentColor = when (filterIdx) {
                                    1 -> Color(0xFFE50914)
                                    2 -> Color(0xFFFFB300)
                                    3 -> Color(0xFF42A5F5)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isFilterSelected) accentColor.copy(alpha = 0.22f) else Color(0xFF1A1F2B),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isFilterSelected) accentColor else Color(0xFF2D3548)
                                    ),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clickable { selectedFavoriteFilter = filterIdx }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isFilterSelected) accentColor else Color(0xFFB0BEC5),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            color = if (isFilterSelected) Color.White else Color(0xFFCFD8DC),
                                            fontSize = 12.sp,
                                            fontWeight = if (isFilterSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Screen Content by Tab
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTabIndex) {
                    0 -> {
                        // TAB 0: HOME / ANASAYFA
                        val heroItem = remember(visibleItems) { visibleItems.firstOrNull { it.type == ItemType.MOVIE && !it.logo.isNullOrEmpty() } ?: visibleItems.firstOrNull() }
                        val popularMovies = remember(visibleItems) { visibleItems.filter { it.type == ItemType.MOVIE }.take(15) }
                        val liveChannels = remember(visibleItems) { visibleItems.filter { it.type == ItemType.LIVE }.take(15) }
                        val seriesItems = remember(visibleItems) { visibleItems.filter { it.type == ItemType.SERIES } }
                        val seriesGroups = remember(seriesItems) { seriesItems.groupBy { it.seriesName ?: it.title } }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            // Hero Banner
                            if (heroItem != null) {
                                item {
                                    HeroBanner(item = heroItem, onClick = {
                                        if (com.example.model.ParentalControlManager.isItemLocked(heroItem)) {
                                            itemToUnlock = heroItem
                                        } else {
                                            playWithPlaylist(heroItem, listOf(heroItem))
                                        }
                                    })
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            // ESPN Match Fixtures
                            if (liveFixtures.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Günün Maçları & Fikstür",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color.White
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isFixturesRefreshing) {
                                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                } else {
                                                    IconButton(
                                                        onClick = { refreshFixtures(true) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = "Fikstürü Güncelle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = stringResource(R.string.filter_all),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable { showAllFixturesSheet = true }
                                                )
                                            }
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(liveFixtures) { fixture ->
                                                MatchFixtureCard(fixture = fixture, onClick = {
                                                    // Find matching live sports channel
                                                    val matchedChannel = allItems.find { channel ->
                                                        channel.type == ItemType.LIVE && fixture.channelKeywords.any { kw -> channel.title.contains(kw, ignoreCase = true) }
                                                    }
                                                    if (matchedChannel != null) {
                                                        if (com.example.model.ParentalControlManager.isItemLocked(matchedChannel)) {
                                                            itemToUnlock = matchedChannel
                                                        } else {
                                                            playWithPlaylist(matchedChannel, listOf(matchedChannel))
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "${fixture.homeTeam} - ${fixture.awayTeam} maç yayını bulunamadı", Toast.LENGTH_SHORT).show()
                                                    }
                                                })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // IMDb / TMDb Upcoming Releases
                            if (imdbReleases.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        if (isPortrait) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Surface(
                                                            color = Color(0xFFF5C518),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                "TMDB",
                                                                color = Color.Black,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 11.sp,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Yakında Vizyondakiler",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 16.sp,
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    if (isMediaDataRefreshing) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 1.5.dp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    } else {
                                                        IconButton(
                                                            onClick = { refreshMediaData(true) },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Refresh,
                                                                contentDescription = "Film/Dizi Verilerini Güncelle",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    items(ImdbTimeFrame.values().toList()) { tf ->
                                                        FilterChip(
                                                            selected = selectedImdbTimeFrame == tf,
                                                            onClick = { selectedImdbTimeFrame = tf },
                                                            label = {
                                                                Text(
                                                                    text = stringResource(tf.labelRes),
                                                                    fontSize = 11.sp,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                            },
                                                            colors = FilterChipDefaults.filterChipColors(
                                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                                selectedLabelColor = Color.White
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        color = Color(0xFFF5C518),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            "TMDB",
                                                            color = Color.Black,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 11.sp,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Yakında Vizyondakiler",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = Color.White
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (isMediaDataRefreshing) {
                                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                                                    } else {
                                                        IconButton(
                                                            onClick = { refreshMediaData(true) },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Default.Refresh, contentDescription = "Film/Dizi Verilerini Güncelle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                    ImdbTimeFrame.values().forEach { tf ->
                                                        FilterChip(
                                                            selected = selectedImdbTimeFrame == tf,
                                                            onClick = { selectedImdbTimeFrame = tf },
                                                            label = { Text(stringResource(tf.labelRes), fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                                            colors = FilterChipDefaults.filterChipColors(
                                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                                selectedLabelColor = Color.White
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(imdbReleases) { item ->
                                                val isNotified = notifiedTmdbIds.contains(item.id)
                                                ImdbCard(
                                                    item = item,
                                                    isNotified = isNotified,
                                                    onNotifyToggle = {
                                                        notifiedTmdbIds = if (isNotified) {
                                                            notifiedTmdbIds - item.id
                                                        } else {
                                                            Toast.makeText(context, "'${item.title}' için bildirim planlandı", Toast.LENGTH_SHORT).show()
                                                            notifiedTmdbIds + item.id
                                                        }
                                                    },
                                                    onClick = { selectedImdbItem = item }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // Recent Progress (Son İzlenenler)
                            if (recentAll.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = stringResource(R.string.continue_watching),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(recentAll) { progress ->
                                                ProgressCard(progress = progress, onClick = {
                                                    val matched = allItems.find { it.url == progress.url }
                                                    if (matched != null) {
                                                        if (com.example.model.ParentalControlManager.isItemLocked(matched)) {
                                                            itemToUnlock = matched
                                                        } else {
                                                            playWithPlaylist(matched, listOf(matched))
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // Popular Movies
                            if (popularMovies.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.tab_movies),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = stringResource(R.string.filter_all),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.clickable { selectedTabIndex = 2 }
                                            )
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(popularMovies) { movie ->
                                                MovieCard(item = movie, onClick = {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(movie)) {
                                                        itemToUnlock = movie
                                                    } else {
                                                        playWithPlaylist(movie, popularMovies)
                                                    }
                                                })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // Popular Series
                            if (seriesGroups.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.tab_series),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = stringResource(R.string.filter_all),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.clickable { selectedTabIndex = 3 }
                                            )
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(seriesGroups.keys.take(15).toList()) { sName ->
                                                val episodes = seriesGroups[sName] ?: emptyList()
                                                val posterItem = episodes.firstOrNull { !it.logo.isNullOrEmpty() } ?: episodes.first()
                                                Box(modifier = Modifier.width(122.dp)) {
                                                    SeriesCard(
                                                        seriesName = sName,
                                                        item = posterItem,
                                                        episodeCount = episodes.size,
                                                        episodes = episodes,
                                                        onPlayEpisode = { ep ->
                                                            if (com.example.model.ParentalControlManager.isItemLocked(ep)) {
                                                                playlistForUnlock = episodes
                                                                itemToUnlock = ep
                                                            } else {
                                                                playWithPlaylist(ep, episodes)
                                                            }
                                                        },
                                                        onClick = {
                                                            if (com.example.model.ParentalControlManager.isItemLocked(posterItem)) {
                                                                seriesToUnlock = episodes
                                                            } else {
                                                                selectedSeries = episodes
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // Live TV Channels
                            if (liveChannels.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.tab_live),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = stringResource(R.string.filter_all),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.clickable { selectedTabIndex = 1 }
                                            )
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(liveChannels) { channel ->
                                                Box(modifier = Modifier.width(130.dp)) {
                                                    ChannelCard(item = channel, onClick = {
                                                        if (com.example.model.ParentalControlManager.isItemLocked(channel)) {
                                                            itemToUnlock = channel
                                                        } else {
                                                            playWithPlaylist(channel, liveChannels)
                                                        }
                                                    })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: LIVE CHANNELS (CANLI)
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(currentTabItems) { channel ->
                                ChannelCard(item = channel, onClick = {
                                    if (com.example.model.ParentalControlManager.isItemLocked(channel)) {
                                        itemToUnlock = channel
                                    } else {
                                        playWithPlaylist(channel, currentTabItems)
                                    }
                                })
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: MOVIES (FLİM)
                        val uncategorizedText = stringResource(R.string.uncategorized)
                        val itemsByGroup = remember(currentTabItems, uncategorizedText) { currentTabItems.groupBy { it.group ?: uncategorizedText } }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            if (currentTabItems.isNotEmpty() && selectedGroup == null && searchQuery.isEmpty()) {
                                val heroMovie = currentTabItems.randomOrNull() ?: currentTabItems.first()
                                item {
                                    HeroBanner(item = heroMovie, onClick = {
                                        if (com.example.model.ParentalControlManager.isItemLocked(heroMovie)) {
                                            itemToUnlock = heroMovie
                                        } else {
                                            playWithPlaylist(heroMovie, currentTabItems)
                                        }
                                    })
                                }
                            }

                            if (recentMovies.isNotEmpty() && selectedGroup == null && searchQuery.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.continue_watching),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(recentMovies) { progress ->
                                            ProgressCard(progress = progress, onClick = {
                                                val matchedItem = allItems.find { it.url == progress.url }
                                                if (matchedItem != null) {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(matchedItem)) {
                                                        itemToUnlock = matchedItem
                                                    } else {
                                                        playWithPlaylist(matchedItem, listOf(matchedItem))
                                                    }
                                                }
                                            })
                                        }
                                    }
                                }
                            }

                            itemsByGroup.forEach { (groupName, groupItems) ->
                                if (groupItems.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = groupName.uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp)
                                        )
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(groupItems) { movie ->
                                                MovieCard(item = movie, onClick = {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(movie)) {
                                                        itemToUnlock = movie
                                                    } else {
                                                        playWithPlaylist(movie, groupItems)
                                                    }
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // TAB 3: SERIES (DİZİ)
                        val seriesGroups = remember(currentTabItems) { currentTabItems.groupBy { it.seriesName ?: it.title } }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (recentSeries.isNotEmpty() && selectedGroup == null && searchQuery.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.continue_watching),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(recentSeries) { progress ->
                                                ProgressCard(progress = progress, onClick = {
                                                    val matchedItem = allItems.find { it.url == progress.url }
                                                    if (matchedItem != null) {
                                                        if (com.example.model.ParentalControlManager.isItemLocked(matchedItem)) {
                                                            itemToUnlock = matchedItem
                                                        } else {
                                                            playWithPlaylist(matchedItem, listOf(matchedItem))
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }

                            items(seriesGroups.keys.toList()) { seriesName ->
                                val episodes = seriesGroups[seriesName] ?: emptyList()
                                val posterItem = episodes.firstOrNull { !it.logo.isNullOrEmpty() } ?: episodes.first()
                                SeriesCard(
                                    seriesName = seriesName,
                                    item = posterItem,
                                    episodeCount = episodes.size,
                                    episodes = episodes,
                                    onPlayEpisode = { ep ->
                                        if (com.example.model.ParentalControlManager.isItemLocked(ep)) {
                                            playlistForUnlock = episodes
                                            itemToUnlock = ep
                                        } else {
                                            playWithPlaylist(ep, episodes)
                                        }
                                    },
                                    onClick = {
                                        if (com.example.model.ParentalControlManager.isItemLocked(posterItem)) {
                                            seriesToUnlock = episodes
                                        } else {
                                            selectedSeries = episodes
                                        }
                                    }
                                )
                            }
                        }
                    }

                    4 -> {
                        // TAB 4: FAVORITES (Filmler, Diziler ve Canlı TV'ler ayrı bölümler halinde)
                        if (currentTabItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(stringResource(R.string.no_favorites_yet), color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        } else {
                            val allSeriesMap = remember(visibleItems) {
                                visibleItems.filter { it.type == ItemType.SERIES }
                                    .groupBy { (it.seriesName ?: it.title).trim().trimEnd('-', '–', '—', ':', '|', ' ').trim() }
                            }
                            val recentFavorites = remember(recentAll, favoriteUrls) {
                                recentAll.filter { favoriteUrls.contains(it.url) }
                            }
                            val favMovies = remember(currentTabItems) {
                                currentTabItems.filter { it.type == ItemType.MOVIE }
                            }
                            val favSeriesGroups = remember(currentTabItems) {
                                currentTabItems.filter { it.type == ItemType.SERIES }
                                    .groupBy { (it.seriesName ?: it.title).trim().trimEnd('-', '–', '—', ':', '|', ' ').trim() }
                                    .entries
                                    .toList()
                            }
                            val favLive = remember(currentTabItems) {
                                currentTabItems.filter { it.type == ItemType.LIVE }
                            }

                            val showMoviesSection = (selectedFavoriteFilter == 0 || selectedFavoriteFilter == 1) && favMovies.isNotEmpty()
                            val showSeriesSection = (selectedFavoriteFilter == 0 || selectedFavoriteFilter == 2) && favSeriesGroups.isNotEmpty()
                            val showLiveSection = (selectedFavoriteFilter == 0 || selectedFavoriteFilter == 3) && favLive.isNotEmpty()

                            val liveBadgeLabel = stringResource(R.string.tab_live)
                            val movieBadgeLabel = stringResource(R.string.tab_movies)

                            if (!showMoviesSection && !showSeriesSection && !showLiveSection) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val emptyIcon = when (selectedFavoriteFilter) {
                                            1 -> Icons.Default.Movie
                                            2 -> Icons.Default.Tv
                                            3 -> Icons.Default.LiveTv
                                            else -> Icons.Default.FavoriteBorder
                                        }
                                        val emptyText = when (selectedFavoriteFilter) {
                                            1 -> stringResource(R.string.fav_empty_movies)
                                            2 -> stringResource(R.string.fav_empty_series)
                                            3 -> stringResource(R.string.fav_empty_live)
                                            else -> stringResource(R.string.no_favorites_yet)
                                        }
                                        Icon(emptyIcon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(emptyText, color = Color.Gray, fontSize = 14.sp)
                                        if (selectedFavoriteFilter != 0) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedButton(
                                                onClick = { selectedFavoriteFilter = 0 },
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(stringResource(R.string.fav_show_all_btn), color = Color.White, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 115.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (recentFavorites.isNotEmpty() && selectedGroup == null && searchQuery.isEmpty() && selectedFavoriteFilter == 0) {
                                        item(key = "fav_recent_section", span = { GridItemSpan(maxLineSpan) }) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = stringResource(R.string.continue_watching),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                )
                                                LazyRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(92.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    items(recentFavorites, key = { "recent_fav_" + it.url }) { progress ->
                                                        ProgressCard(progress = progress, onClick = {
                                                            val matchedItem = allItems.find { it.url == progress.url }
                                                            if (matchedItem != null) {
                                                                if (com.example.model.ParentalControlManager.isItemLocked(matchedItem)) {
                                                                    itemToUnlock = matchedItem
                                                                } else {
                                                                    playWithPlaylist(matchedItem, currentTabItems)
                                                                }
                                                            }
                                                        })
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }

                                    // 1. FİLMLER BÖLÜMÜ
                                    if (showMoviesSection) {
                                        item(key = "fav_header_movies", span = { GridItemSpan(maxLineSpan) }) {
                                            Surface(
                                                color = Color(0xFF161B24),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.38f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp, bottom = 2.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(4.dp)
                                                                .height(18.dp)
                                                                .background(Color(0xFFE50914), RoundedCornerShape(2.dp))
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Movie,
                                                            contentDescription = null,
                                                            tint = Color(0xFFE50914),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = stringResource(R.string.fav_section_movies),
                                                            color = Color.White,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 15.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Surface(
                                                            color = Color(0xFFE50914).copy(alpha = 0.2f),
                                                            shape = RoundedCornerShape(12.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.fav_count_movies, favMovies.size),
                                                                color = Color(0xFFFF6B6B),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    if (selectedFavoriteFilter == 0 && (favSeriesGroups.isNotEmpty() || favLive.isNotEmpty())) {
                                                        Text(
                                                            text = stringResource(R.string.filter_all),
                                                            color = Color(0xFFE50914),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.clickable { selectedFavoriteFilter = 1 }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        items(favMovies, key = { "fav_movie_" + it.url.ifBlank { it.title } }) { item ->
                                            MovieCard(
                                                item = item,
                                                modifier = Modifier.fillMaxWidth(),
                                                badgeText = item.group?.takeIf { it.isNotBlank() } ?: movieBadgeLabel,
                                                onClick = {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                                                        itemToUnlock = item
                                                    } else {
                                                        playWithPlaylist(item, favMovies)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    // 2. DİZİLER BÖLÜMÜ
                                    if (showSeriesSection) {
                                        item(key = "fav_header_series", span = { GridItemSpan(maxLineSpan) }) {
                                            Surface(
                                                color = Color(0xFF161B24),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.38f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = if (showMoviesSection) 12.dp else 4.dp, bottom = 2.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(4.dp)
                                                                .height(18.dp)
                                                                .background(Color(0xFFFFB300), RoundedCornerShape(2.dp))
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Tv,
                                                            contentDescription = null,
                                                            tint = Color(0xFFFFB300),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = stringResource(R.string.fav_section_series),
                                                            color = Color.White,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 15.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Surface(
                                                            color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                                            shape = RoundedCornerShape(12.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.fav_count_series, favSeriesGroups.size),
                                                                color = Color(0xFFFFCA28),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    if (selectedFavoriteFilter == 0 && (favMovies.isNotEmpty() || favLive.isNotEmpty())) {
                                                        Text(
                                                            text = stringResource(R.string.filter_all),
                                                            color = Color(0xFFFFB300),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.clickable { selectedFavoriteFilter = 2 }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        items(favSeriesGroups, key = { "fav_series_" + it.key }) { (sName, favEpisodes) ->
                                            val primaryFavItem = favEpisodes.first()
                                            val episodes = allSeriesMap[sName]?.takeIf { it.isNotEmpty() } ?: favEpisodes
                                            val fallbackLogo = episodes.firstOrNull { !it.logo.isNullOrEmpty() }?.logo
                                            val cardItem = if (primaryFavItem.logo.isNullOrEmpty() && !fallbackLogo.isNullOrEmpty()) {
                                                primaryFavItem.copy(logo = fallbackLogo)
                                            } else {
                                                primaryFavItem
                                            }
                                            SeriesCard(
                                                seriesName = sName,
                                                item = cardItem,
                                                episodeCount = episodes.size,
                                                episodes = episodes,
                                                onPlayEpisode = { ep ->
                                                    if (com.example.model.ParentalControlManager.isItemLocked(ep)) {
                                                        playlistForUnlock = episodes
                                                        itemToUnlock = ep
                                                    } else {
                                                        playWithPlaylist(ep, episodes)
                                                    }
                                                },
                                                onClick = {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(cardItem)) {
                                                        seriesToUnlock = episodes
                                                    } else {
                                                        selectedSeries = episodes
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    // 3. CANLI TV'LER BÖLÜMÜ
                                    if (showLiveSection) {
                                        item(key = "fav_header_live", span = { GridItemSpan(maxLineSpan) }) {
                                            Surface(
                                                color = Color(0xFF161B24),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFF42A5F5).copy(alpha = 0.38f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = if (showMoviesSection || showSeriesSection) 12.dp else 4.dp, bottom = 2.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(4.dp)
                                                                .height(18.dp)
                                                                .background(Color(0xFF42A5F5), RoundedCornerShape(2.dp))
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.LiveTv,
                                                            contentDescription = null,
                                                            tint = Color(0xFF42A5F5),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = stringResource(R.string.fav_section_live),
                                                            color = Color.White,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 15.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Surface(
                                                            color = Color(0xFF42A5F5).copy(alpha = 0.2f),
                                                            shape = RoundedCornerShape(12.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.fav_count_live, favLive.size),
                                                                color = Color(0xFF90CAF9),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    if (selectedFavoriteFilter == 0 && (favMovies.isNotEmpty() || favSeriesGroups.isNotEmpty())) {
                                                        Text(
                                                            text = stringResource(R.string.filter_all),
                                                            color = Color(0xFF42A5F5),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.clickable { selectedFavoriteFilter = 3 }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        items(favLive, key = { "fav_live_" + it.url.ifBlank { it.title } }) { item ->
                                            MovieCard(
                                                item = item,
                                                modifier = Modifier.fillMaxWidth(),
                                                badgeText = item.group?.takeIf { it.isNotBlank() } ?: liveBadgeLabel,
                                                onClick = {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                                                        itemToUnlock = item
                                                    } else {
                                                        playWithPlaylist(item, favLive)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Drawer Panel Overlay
        androidx.compose.animation.AnimatedVisibility(
                    visible = showCategoryDrawer,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val pinCodeVal by com.example.model.ParentalControlManager.pinCode.collectAsState()
                    val hasPin = pinCodeVal != null
                    val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
                    var groupToUnlock by remember { mutableStateOf<String?>(null) }

                    if (groupToUnlock != null) {
                        com.example.ui.components.PinUnlockDialog(
                            onUnlock = {
                                val grp = groupToUnlock
                                groupToUnlock = null
                                if (grp != null) {
                                    com.example.model.ParentalControlManager.toggleGroupLock(grp)
                                    Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDismiss = { groupToUnlock = null }
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { showCategoryDrawer = false }
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(top = 15.dp, bottom = 15.dp)
                                .fillMaxWidth(0.82f)
                                .widthIn(min = 280.dp, max = 360.dp)
                                .clickable(enabled = false) {},
                            color = Color(0xFF141414),
                            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                            shadowElevation = 16.dp
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.List,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.categories).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                    IconButton(onClick = { showCategoryDrawer = false }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.close_desc),
                                            tint = Color.Gray
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    item {
                                        val isAllSelected = selectedGroup == null
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedGroup = null
                                                    showCategoryDrawer = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = stringResource(R.string.filter_all).uppercase(),
                                                color = if (isAllSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                            Text(
                                                text = "$totalTypeCount",
                                                color = if (isAllSelected) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }

                                    items(displayGroups.size) { index ->
                                        val group = displayGroups[index]
                                        val isSelected = selectedGroup == group
                                        val count = groupCounts[group] ?: 0

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedGroup = group
                                                    showCategoryDrawer = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = group.uppercase(),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "$count",
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                val isGroupLocked = hasPin && lockedGroups.contains(group)

                                                IconButton(
                                                    onClick = {
                                                        if (!hasPin) {
                                                            Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                                                        } else if (isGroupLocked) {
                                                            groupToUnlock = group
                                                        } else {
                                                            com.example.model.ParentalControlManager.toggleGroupLock(group)
                                                            Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        if (isGroupLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                        contentDescription = null,
                                                        tint = if (isGroupLocked) Color(0xFFEF5350) else Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

    // Series Detail Bottom Sheet
    val currentSeries = selectedSeries
    if (currentSeries != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedSeries = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null
        ) {
            SeriesDetailSheet(
                items = currentSeries,
                onPlayStream = { item ->
                    selectedSeries = null
                    if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                        playlistForUnlock = currentSeries
                        itemToUnlock = item
                    } else {
                        playWithPlaylist(item, currentSeries)
                    }
                },
                onClose = { selectedSeries = null }
            )
        }
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null
        ) {
            SettingsSheetContent(
                onClose = { showSettingsSheet = false },
                onOpenSupport = {
                    showSettingsSheet = false
                    showSupportSheet = true
                }
            )
        }
    }

    // Profile Settings Bottom Sheet
    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null
        ) {
            ProfileSettingsSheet(
                onClose = { showProfileSheet = false },
                onOpenSupport = {
                    showProfileSheet = false
                    showSupportSheet = true
                },
                onNavigateToAuth = {
                    showProfileSheet = false
                    onNavigateToAuth()
                }
            )
        }
    }

    // Support Tickets Bottom Sheet
    if (showSupportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSupportSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null
        ) {
            SupportTicketsSheet(onClose = { showSupportSheet = false })
        }
    }

    // All Fixtures Bottom Sheet
    if (showAllFixturesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllFixturesSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null
        ) {
            com.example.util.KeepSystemBarsHidden()
            var fixtureFilterLeague by remember { mutableStateOf("Tümü") }
            val leagues = listOf("Tümü", "Trendyol Süper Lig", "UEFA Şampiyonlar Ligi", "UEFA Avrupa Ligi", "Canlı")
            val filteredFixtures = remember(liveFixtures, fixtureFilterLeague, searchQuery) {
                liveFixtures.filter { fixture ->
                    val matchesLeague = when (fixtureFilterLeague) {
                        "Tümü" -> true
                        "Canlı" -> fixture.isLive
                        else -> fixture.league.contains(fixtureFilterLeague, ignoreCase = true)
                    }
                    val matchesSearch = searchQuery.isBlank() ||
                        fixture.homeTeam.contains(searchQuery, ignoreCase = true) ||
                        fixture.awayTeam.contains(searchQuery, ignoreCase = true) ||
                        fixture.league.contains(searchQuery, ignoreCase = true)
                    matchesLeague && matchesSearch
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Günün Maçları & Fikstür",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFixturesRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Güncelleniyor...", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            OutlinedButton(
                                onClick = { refreshFixtures(true) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Fikstürü Güncelle", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Güncelle", fontSize = 11.sp, color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showAllFixturesSheet = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(leagues) { lg ->
                        FilterChip(
                            selected = fixtureFilterLeague == lg,
                            onClick = { fixtureFilterLeague = lg },
                            label = { Text(lg, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E2430),
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }

                if (filteredFixtures.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Bu filtreye uygun maç fikstürü bulunamadı.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredFixtures, key = { it.id }) { fixture ->
                            MatchFixtureDetailedCard(
                                fixture = fixture,
                                onWatchClick = {
                                    val matchedChannel = allItems.find { channel ->
                                        channel.type == ItemType.LIVE && fixture.channelKeywords.any { kw -> channel.title.contains(kw, ignoreCase = true) }
                                    }
                                    if (matchedChannel != null) {
                                        showAllFixturesSheet = false
                                        if (com.example.model.ParentalControlManager.isItemLocked(matchedChannel)) {
                                            itemToUnlock = matchedChannel
                                        } else {
                                            playWithPlaylist(matchedChannel, listOf(matchedChannel))
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "${fixture.broadcastChannel} kanalı çalma listenizde bulunamadı.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // TMDb Detail Dialog
    val currentImdbItem = selectedImdbItem
    if (currentImdbItem != null) {
        val isNotified = notifiedTmdbIds.contains(currentImdbItem.id)
        ImdbDetailDialog(
            item = currentImdbItem,
            isNotified = isNotified,
            onNotifyToggle = {
                notifiedTmdbIds = if (isNotified) {
                    notifiedTmdbIds - currentImdbItem.id
                } else {
                    Toast.makeText(context, "'${currentImdbItem.title}' için bildirim planlandı", Toast.LENGTH_SHORT).show()
                    notifiedTmdbIds + currentImdbItem.id
                }
            },
            onDismiss = { selectedImdbItem = null }
        )
    }

    // Pro Upgrade Restriction Dialog
    if (showProUpgradeDialog) {
        com.example.ui.components.ProUpgradeDialog(
            onDismiss = { showProUpgradeDialog = false }
        )
    }

    // Version Update Dialog
    com.example.ui.components.UpdateDialog()
}
}
