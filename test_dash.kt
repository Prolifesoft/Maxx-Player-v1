package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import coil.compose.AsyncImage
import com.example.model.PlaylistRepository
import com.example.parser.M3uItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onPlayStream: (M3uItem) -> Unit, onNavigateToAuth: () -> Unit = {}) {
    val playWithPlaylist: (M3uItem, List<M3uItem>) -> Unit = { item, playlist ->
        com.example.model.PlayerRepository.currentPlaylist = playlist
        onPlayStream(item)
    }
    val currentLang by com.example.model.AppLanguageManager.currentLanguage.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(2) } // Default to Live TV
    val tabs = listOf(stringResource(R.string.tab_movies), stringResource(R.string.tab_series), stringResource(R.string.tab_live))

    val allItems by PlaylistRepository.playlist.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { com.example.model.db.AppDatabase.getDatabase(context) }
    val recentMovies by db.iptvDao().getRecentProgressForType("MOVIE").collectAsState(initial = emptyList())
    val recentSeries by db.iptvDao().getRecentProgressForType("SERIES").collectAsState(initial = emptyList())

    
    val currentType = remember(selectedTabIndex) {
        when(selectedTabIndex) {
            0 -> com.example.parser.ItemType.MOVIE
            1 -> com.example.parser.ItemType.SERIES
            else -> com.example.parser.ItemType.LIVE
        }
    }
    
    val groups = remember(allItems, currentType) { PlaylistRepository.getGroups(currentType) }
    var selectedGroup by remember(currentType) { mutableStateOf<String?>(null) } // reset on tab change
    var searchQuery by remember(currentType) { mutableStateOf("") }
    var showCategoryDrawer by remember(currentType) { mutableStateOf(false) }

    val groupCounts = remember(allItems, currentType) {
        allItems.filter { it.type == currentType }
            .groupingBy { it.group ?: "" }
            .eachCount()
    }
    val totalTypeCount = remember(allItems, currentType) {
        allItems.count { it.type == currentType }
    }
    
    val filteredItems = remember(allItems, selectedGroup, currentType, searchQuery) {
        PlaylistRepository.getItemsForGroup(selectedGroup, currentType).filter {
            it.title.contains(searchQuery, ignoreCase = true)
        }
    }

    var selectedSeries by remember { mutableStateOf<List<com.example.parser.M3uItem>?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }
    var showGeneralReportDialog by remember { mutableStateOf(false) }

    var itemToUnlock by remember { mutableStateOf<com.example.parser.M3uItem?>(null) }
    var seriesToUnlock by remember { mutableStateOf<List<com.example.parser.M3uItem>?>(null) }

    if (itemToUnlock != null) {
        com.example.ui.components.PinUnlockDialog(
            onUnlock = {
                val item = itemToUnlock
                itemToUnlock = null
                if (item != null) onPlayStream(item)
            },
            onDismiss = { itemToUnlock = null }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(
                    onClick = {
                        com.example.model.FavoritesManager.toggleFavorite(item.url)
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        if (isFavorite) androidx.compose.material.icons.Icons.Default.Favorite else androidx.compose.material.icons.Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { showGeneralReportDialog = true }) {
                        Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043))
                    }
                    IconButton(
                    onClick = {
                        com.example.model.FavoritesManager.toggleFavorite(item.url)
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        if (isFavorite) androidx.compose.material.icons.Icons.Default.Favorite else androidx.compose.material.icons.Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { showSupportSheet = true }) {
                        Icon(Icons.Default.SupportAgent, contentDescription = stringResource(R.string.support_tickets_title), tint = Color(0xFF42A5F5))
                    }
                    IconButton(onClick = { showProfileSheet = true }) {
                        Icon(Icons.Default.Person, contentDescription = stringResource(R.string.profile_desc), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_desc), tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                val notification by com.example.model.SupportRepository.unreadNotification.collectAsState()
                if (notification != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable {
                                showSupportSheet = true
                                com.example.model.SupportRepository.dismissNotification()
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.notification_banner_title), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(stringResource(R.string.notification_banner_msg), color = Color.LightGray, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                showSupportSheet = true
                                com.example.model.SupportRepository.dismissNotification()
                            }) {
                                Text(stringResource(R.string.view_ticket_btn), color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { com.example.model.SupportRepository.dismissNotification() }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { /* No custom indicator needed, just standard */ }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    title, 
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else Color.Gray,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
                
                // Search Bar & Category Icon Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_placeholder), tint = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                    
                    if (groups.isNotEmpty()) {
                        IconButton(
                            onClick = { showCategoryDrawer = true },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (selectedGroup != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(14.dp)
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

                if (selectedTabIndex == 2) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems) { item ->
                        ChannelCard(item = item, onClick = { 
                            if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                                itemToUnlock = item
                            } else {
                                playWithPlaylist(item, filteredItems)
                            }
                        })
                    }
                }
            } else if (selectedTabIndex == 1) { // Series
                val seriesGroups = remember(filteredItems) { filteredItems.groupBy { it.seriesName ?: it.title } }
                
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    
                    if (recentSeries.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    text = stringResource(R.string.continue_watching),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
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
                    if (seriesGroups.isNotEmpty()) {
                        val heroSeriesName = seriesGroups.keys.randomOrNull() ?: seriesGroups.keys.first()
                        val heroEpisodes = seriesGroups[heroSeriesName] ?: emptyList()
                        val heroItem = heroEpisodes.firstOrNull { !it.logo.isNullOrEmpty() } ?: heroEpisodes.first()
                        
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Column {
                                HeroBanner(item = heroItem, onClick = {
                                    if (com.example.model.ParentalControlManager.isItemLocked(heroItem)) {
                                        seriesToUnlock = heroEpisodes
                                    } else {
                                        selectedSeries = heroEpisodes
                                    }
                                })
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    items(seriesGroups.keys.toList()) { seriesName ->
                        val episodes = seriesGroups[seriesName] ?: emptyList()
                        val posterItem = episodes.firstOrNull { !it.logo.isNullOrEmpty() } ?: episodes.first()
                        // Use a custom card for series that displays the title nicely
                        SeriesCard(seriesName = seriesName, item = posterItem, episodeCount = episodes.size, onClick = { 
                            if (com.example.model.ParentalControlManager.isItemLocked(posterItem)) {
                                seriesToUnlock = episodes
                            } else {
                                selectedSeries = episodes
                            }
                        })
                    }
                }
            } else { // Movies
                val uncategorizedText = stringResource(R.string.uncategorized)
                val itemsByGroup = remember(filteredItems, uncategorizedText) { filteredItems.groupBy { it.group ?: uncategorizedText } }
                
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredItems.isNotEmpty()) {
                        val heroItem = filteredItems.randomOrNull() ?: filteredItems.first()
                        item {
                            HeroBanner(item = heroItem, onClick = { 
                                if (com.example.model.ParentalControlManager.isItemLocked(heroItem)) {
                                    itemToUnlock = heroItem
                                } else {
                                    playWithPlaylist(heroItem, filteredItems)
                                }
                            })
                        }
                    }
                    
                    
                    if (recentMovies.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.continue_watching),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp)
                            )
                            androidx.compose.foundation.lazy.LazyRow(
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
                                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp, end = 16.dp)
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(groupItems) { item ->
                                        MovieCard(item = item, onClick = { 
                                            if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                                                itemToUnlock = item
                                            } else {
                                                playWithPlaylist(item, filteredItems)
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
            }
            
            // Category Drawer Overlay (opens from left when showCategoryDrawer is true)
            AnimatedVisibility(
                visible = showCategoryDrawer,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.fillMaxSize()
            ) {
                BackHandler(enabled = showCategoryDrawer) {
                    showCategoryDrawer = false
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    // Dimmed Backdrop Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable { showCategoryDrawer = false }
                    )
                    
                    // Left Side Drawer Panel
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.82f)
                            .widthIn(min = 280.dp, max = 360.dp)
                            .clickable(enabled = false) {}, // intercept clicks so they don't hit the scrim
                        color = Color(0xFF141414),
                        shadowElevation = 16.dp
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Drawer Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
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
                            
                            // Category List
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                // "ALL" Item
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
                                
                                // Group Items
                                items(groups.size) { index ->
                                    val group = groups[index]
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
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "$count",
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
                                            val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
                                            val isGroupLocked = hasPin && lockedGroups.contains(group)
                                            var showGroupUnlockDialog by remember { mutableStateOf(false) }

                                            IconButton(
                                                onClick = {
                                                    if (!hasPin) {
                                                        android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                                                    } else if (isGroupLocked) {
                                                        showGroupUnlockDialog = true
                                                    } else {
                                                        com.example.model.ParentalControlManager.toggleGroupLock(group)
                                                        android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_locked), android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    if (isGroupLocked) Icons.Default.Lock else androidx.compose.material.icons.Icons.Default.LockOpen,
                                                    contentDescription = null,
                                                    tint = if (isGroupLocked) Color(0xFFEF5350) else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            
                                            if (showGroupUnlockDialog) {
                                                com.example.ui.components.PinUnlockDialog(
                                                    onUnlock = {
                                                        com.example.model.ParentalControlManager.toggleGroupLock(group)
                                                        showGroupUnlockDialog = false
                                                        android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    onDismiss = { showGroupUnlockDialog = false }
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
    }
    
    if (selectedSeries != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedSeries = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            com.example.ui.ProvideAppLocale(currentLang) {
                SeriesDetailSheet(items = selectedSeries!!, onPlayStream = { item -> 
                    selectedSeries = null
                    if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                        itemToUnlock = item
                    } else {
                        playWithPlaylist(item, selectedSeries!!)
                    }
                })
            }
        }
    }
    
    
    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            com.example.ui.ProvideAppLocale(currentLang) {
                ProfileSettingsSheet(onNavigateToAuth = onNavigateToAuth)
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            com.example.ui.ProvideAppLocale(currentLang) {
                SettingsSheetContent(onClose = { showSettingsSheet = false }, onOpenSupport = { showSupportSheet = true })
            }
        }
    }

    if (showSupportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSupportSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            com.example.ui.ProvideAppLocale(currentLang) {
                SupportTicketsSheet(onClose = { showSupportSheet = false })
            }
        }
    }

    if (showGeneralReportDialog) {
        val activeTitle = when(currentType) {
            com.example.parser.ItemType.MOVIE -> context.getString(R.string.tab_movies)
            com.example.parser.ItemType.SERIES -> context.getString(R.string.tab_series)
            else -> context.getString(R.string.tab_live)
        }
        com.example.ui.components.ReportConfirmDialog(
            title = "Genel Sorun ($activeTitle)",
            channelName = activeTitle,
            message = "$activeTitle listesinde veya oynatıcısında sorun bildirildi.",
            onDismiss = { showGeneralReportDialog = false }
        )
    }
}

@Composable
fun HeroBanner(item: M3uItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clickable(onClick = onClick)
    ) {
        if (!item.logo.isNullOrEmpty()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(item.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VideoLibrary, 
                    contentDescription = null, 
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        startY = 100f
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.play))
            }
        }
    }
}

@Composable
fun MovieCard(item: M3uItem, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!item.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(item.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VideoLibrary, 
                        contentDescription = null, 
                        modifier = Modifier.size(40.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                }
            }
            val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
            val isFavorite = favoriteUrls.contains(item.url)
            var showReportDialog by remember { mutableStateOf(false) }
            val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
            val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
            val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
            val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
            var showUnlockDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            ) {
                IconButton(
                    onClick = {
                        if (!hasPin) {
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                        } else if (isLocked) {
                            showUnlockDialog = true
                        } else {
                            com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_locked), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else androidx.compose.material.icons.Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isLocked) Color(0xFFEF5350) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(16.dp))
                }
            }
            if (showUnlockDialog) {
                com.example.ui.components.PinUnlockDialog(
                    onUnlock = {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        showUnlockDialog = false
                        android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showUnlockDialog = false }
                )
            }
            if (showReportDialog) {
                com.example.ui.components.ReportConfirmDialog(
                    title = "Yayın Sorunu: ${item.title}",
                    channelName = item.title,
                    message = "'${item.title}' filminde oynatma veya akış sorunu bildirildi.",
                    onDismiss = { showReportDialog = false }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(4.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 10.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ChannelCard(item: M3uItem, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (!item.logo.isNullOrEmpty()) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(item.logo)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                        error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                    )
                } else {
                    Icon(
                        Icons.Default.VideoLibrary, 
                        contentDescription = null, 
                        modifier = Modifier.size(60.dp),
                        tint = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
            val isFavorite = favoriteUrls.contains(item.url)
            var showReportDialog by remember { mutableStateOf(false) }
            val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
            val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
            val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
            val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
            var showUnlockDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            ) {
                IconButton(
                    onClick = {
                        if (!hasPin) {
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                        } else if (isLocked) {
                            showUnlockDialog = true
                        } else {
                            com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_locked), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else androidx.compose.material.icons.Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isLocked) Color(0xFFEF5350) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(16.dp))
                }
            }
            if (showUnlockDialog) {
                com.example.ui.components.PinUnlockDialog(
                    onUnlock = {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        showUnlockDialog = false
                        android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showUnlockDialog = false }
                )
            }
            if (showReportDialog) {
                com.example.ui.components.ReportConfirmDialog(
                    title = "Canlı Yayın Sorunu: ${item.title}",
                    channelName = item.title,
                    message = "'${item.title}' canlı yayınında kesinti veya açılmama sorunu bildirildi.",
                    onDismiss = { showReportDialog = false }
                )
            }
        }
    }
}

@Composable
fun SeriesCard(seriesName: String, item: M3uItem, episodeCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!item.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = seriesName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VideoLibrary, 
                        contentDescription = null, 
                        modifier = Modifier.size(40.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                }
            }
            
            val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
            val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
            val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
            val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
            var showUnlockDialog by remember { mutableStateOf(false) }
            val context = androidx.compose.ui.platform.LocalContext.current
            
            IconButton(
                onClick = {
                    if (!hasPin) {
                        android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                    } else if (isLocked) {
                        showUnlockDialog = true
                    } else {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_locked), android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            ) {
                Icon(
                    if (isLocked) Icons.Default.Lock else androidx.compose.material.icons.Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isLocked) Color(0xFFEF5350) else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            
            if (showUnlockDialog) {
                com.example.ui.components.PinUnlockDialog(
                    onUnlock = {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        showUnlockDialog = false
                        android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showUnlockDialog = false }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = seriesName,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        text = "$episodeCount ${stringResource(R.string.episodes_suffix)}",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesDetailSheet(items: List<M3uItem>, onPlayStream: (M3uItem) -> Unit) {
    val seriesName = items.firstOrNull()?.seriesName ?: items.firstOrNull()?.title ?: stringResource(R.string.series_fallback)
    val groupedBySeason = remember(items) { items.groupBy { it.season ?: 1 }.toSortedMap() }
    val seasons = groupedBySeason.keys.toList()
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f) // Take most of the screen
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = seriesName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(16.dp)
        )
        
        if (seasons.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedSeasonIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color.White,
                edgePadding = 16.dp,
                divider = { Divider(color = Color.DarkGray) }
            ) {
                seasons.forEachIndexed { index, season ->
                    Tab(
                        selected = selectedSeasonIndex == index,
                        onClick = { selectedSeasonIndex = index },
                        text = { Text("${stringResource(R.string.season_prefix)} $season", fontWeight = FontWeight.Bold) }
                    )
                }
            }
            
            val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
            val episodes = groupedBySeason[selectedSeason] ?: emptyList()
            
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Ensure it takes remaining space and is scrollable
            ) {
                items(episodes.sortedBy { it.episode ?: 0 }) { episode ->
                    val epTitle = "${stringResource(R.string.episode_prefix)} ${episode.episode ?: "?"}: ${episode.title}"
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayStream(episode) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = epTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        var showEpReportDialog by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showEpReportDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(18.dp))
                        }
                        if (showEpReportDialog) {
                            com.example.ui.components.ReportConfirmDialog(
                                title = "Dizi Sorunu: ${seriesName} ($epTitle)",
                                channelName = "${seriesName} ($epTitle)",
                                message = "'${seriesName} - $epTitle' bölümünde oynatma sorunu bildirildi.",
                                onDismiss = { showEpReportDialog = false }
                            )
                        }
                    }
                    Divider(color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSheetContent(onClose: () -> Unit, onOpenSupport: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { com.example.model.db.AppDatabase.getDatabase(context) }
    
    var showFaqDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onClose) {
                Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
            }
        }
        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

        var showParentalDialog by remember { mutableStateOf(false) }
        Text(stringResource(R.string.settings_parental), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Lock, title = stringResource(R.string.parental_control_title), iconTint = Color(0xFFE53935), onClick = { showParentalDialog = true })
        if (showParentalDialog) {
            com.example.ui.components.ParentalControlDialog(onDismiss = { showParentalDialog = false })
        }
        
        Text(stringResource(R.string.settings_data), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Delete, title = stringResource(R.string.settings_erase_recent), iconTint = Color(0xFF42A5F5), onClick = {
            coroutineScope.launch {
                db.iptvDao().clearRecentProgress()
                Toast.makeText(context, context.getString(R.string.toast_recent_cleared), Toast.LENGTH_SHORT).show()
                onClose()
            }
        })
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Update, title = stringResource(R.string.settings_update_movies), iconTint = Color(0xFF42A5F5), onClick = {
            coroutineScope.launch {
                Toast.makeText(context, context.getString(R.string.toast_movies_updated), Toast.LENGTH_SHORT).show()
                onClose()
            }
        })
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Update, title = stringResource(R.string.settings_update_series), iconTint = Color(0xFFAB47BC), onClick = {
            coroutineScope.launch {
                Toast.makeText(context, context.getString(R.string.toast_series_updated), Toast.LENGTH_SHORT).show()
                onClose()
            }
        })
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Update, title = stringResource(R.string.settings_update_live), iconTint = Color(0xFF66BB6A), onClick = {
            coroutineScope.launch {
                Toast.makeText(context, context.getString(R.string.toast_live_updated), Toast.LENGTH_SHORT).show()
                onClose()
            }
        })
        
        Text(stringResource(R.string.settings_share), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.RateReview, title = stringResource(R.string.settings_review), iconTint = Color(0xFFFFA726), onClick = { showRateDialog = true })
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Star, title = stringResource(R.string.settings_rate_us), iconTint = Color(0xFFEF5350), onClick = { showRateDialog = true })
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Share, title = stringResource(R.string.settings_share_family), iconTint = Color(0xFFBDBDBD), onClick = {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_share_family)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })
        
        Text(stringResource(R.string.settings_help), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Help, title = stringResource(R.string.settings_faq), iconTint = Color(0xFFAB47BC), onClick = { showFaqDialog = true })
        
        Text(stringResource(R.string.settings_contact), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Report, title = stringResource(R.string.settings_report), iconTint = Color(0xFFFF7043), onClick = { onClose(); onOpenSupport() })
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Add, title = stringResource(R.string.settings_request), iconTint = Color(0xFFEF5350), onClick = { onClose(); onOpenSupport() })
        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Build, title = stringResource(R.string.settings_support), iconTint = Color(0xFF42A5F5), onClick = { onClose(); onOpenSupport() })
    }

    if (showFaqDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = { Text(stringResource(R.string.faq_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.faq_q1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.faq_a1), color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.faq_q2), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.faq_a2), color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.faq_q3), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.faq_a3), color = Color.LightGray, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showFaqDialog = false }) {
                    Text(stringResource(R.string.close_desc))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showRateDialog) {
        var selectedStars by remember { mutableStateOf(5) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text(stringResource(R.string.rate_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rate_desc), color = Color.LightGray, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= selectedStars) androidx.compose.material.icons.Icons.Default.Star else androidx.compose.material.icons.Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { selectedStars = star }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showRateDialog = false
                    Toast.makeText(context, context.getString(R.string.rate_thanks), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.rate_submit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRateDialog = false }) {
                    Text(stringResource(R.string.close_desc), color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showReportDialog) {
        var reportText by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(stringResource(R.string.report_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(stringResource(R.string.report_desc), color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reportText,
                        onValueChange = { reportText = it },
                        placeholder = { Text(stringResource(R.string.report_placeholder), color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        Toast.makeText(context, context.getString(R.string.report_sent), Toast.LENGTH_SHORT).show()
                    },
                    enabled = reportText.isNotBlank()
                ) {
                    Text(stringResource(R.string.report_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text(stringResource(R.string.close_desc), color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, iconTint: Color, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconTint.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun ProgressCard(progress: com.example.model.db.PlaybackProgressEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .aspectRatio(16f/9f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!progress.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(progress.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = progress.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VideoLibrary, 
                        contentDescription = null, 
                        modifier = Modifier.size(40.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = progress.title,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs.toFloat()) else 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.DarkGray
                    )
                }
            }
        }
    }
}


@Composable
fun ProfileSettingsSheet(onNavigateToAuth: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentLang by com.example.model.AppLanguageManager.currentLanguage.collectAsState()
    val db = remember { com.example.model.db.AppDatabase.getDatabase(context) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var currentUser by remember { mutableStateOf<com.example.model.db.UserEntity?>(null) }

    LaunchedEffect(Unit) {
        currentUser = db.iptvDao().getFirstUser()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(stringResource(com.example.R.string.personalization_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(stringResource(com.example.R.string.language_selection), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
            com.example.model.AppLanguageManager.setLanguage(context, "tr")
        }.padding(vertical = 8.dp)) {
            RadioButton(selected = currentLang == "tr", onClick = { com.example.model.AppLanguageManager.setLanguage(context, "tr") })
            Spacer(modifier = Modifier.width(8.dp))
            Text("Türkçe", color = Color.White)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
            com.example.model.AppLanguageManager.setLanguage(context, "en")
        }.padding(vertical = 8.dp)) {
            RadioButton(selected = currentLang == "en", onClick = { com.example.model.AppLanguageManager.setLanguage(context, "en") })
            Spacer(modifier = Modifier.width(8.dp))
            Text("English", color = Color.White)
        }
        
        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
        
        Text(stringResource(com.example.R.string.account_management), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(com.example.R.string.google_account_connected), fontSize = 11.sp, color = Color.Gray)
                Text(currentUser?.email ?: currentUser?.name ?: "ncem0332006@gmail.com", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToAuth() }
                .padding(vertical = 10.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFFA726))
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(com.example.R.string.sign_out_account), color = Color.White, fontSize = 15.sp)
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToAuth() }
                .padding(vertical = 10.dp)
        ) {
            Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = Color(0xFF42A5F5))
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(com.example.R.string.switch_account), color = Color.White, fontSize = 15.sp)
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    coroutineScope.launch {
                        db.iptvDao().clearUsers()
                        db.iptvDao().clearRecentProgress()
                        android.widget.Toast.makeText(context, context.getString(com.example.R.string.account_removed_toast), android.widget.Toast.LENGTH_SHORT).show()
                        onNavigateToAuth()
                    }
                }
                .padding(vertical = 10.dp)
        ) {
            Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color(0xFFEF5350))
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(com.example.R.string.remove_account), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportTicketsSheet(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tickets by com.example.model.SupportRepository.tickets.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    var newMessage by remember { mutableStateOf("") }
    var showNewTicketForm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Color(0xFF42A5F5), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.support_tickets_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
            }
        }
        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

        if (showNewTicketForm) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.ticket_new_btn), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text(stringResource(R.string.ticket_title_label), color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF42A5F5), unfocusedBorderColor = Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        placeholder = { Text(stringResource(R.string.ticket_msg_label), color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF42A5F5), unfocusedBorderColor = Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showNewTicketForm = false }) {
                            Text(stringResource(R.string.close_desc), color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTitle.isNotBlank() && newMessage.isNotBlank()) {
                                    com.example.model.SupportRepository.createTicket(newTitle, null, newMessage)
                                    newTitle = ""
                                    newMessage = ""
                                    showNewTicketForm = false
                                    android.widget.Toast.makeText(context, context.getString(R.string.report_sent), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))
                        ) {
                            Text(stringResource(R.string.report_send), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = { showNewTicketForm = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ticket_new_btn), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (tickets.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.uncategorized), color = Color.Gray)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tickets.size) { index ->
                    val ticket = tickets[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ticket.title,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = if (ticket.status == "Yanıtlandı") Color(0xFF1B5E20) else Color(0xFFE65100),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (ticket.status == "Yanıtlandı") stringResource(R.string.ticket_status_resolved) else stringResource(R.string.ticket_status_open),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = ticket.timestamp, fontSize = 10.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = ticket.message, fontSize = 12.sp, color = Color.LightGray)
                            
                            if (ticket.adminReply != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF263238), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(stringResource(R.string.ticket_admin_reply_label), fontWeight = FontWeight.Bold, color = Color(0xFF69F0AE), fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = ticket.adminReply, color = Color.White, fontSize = 12.sp)
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
