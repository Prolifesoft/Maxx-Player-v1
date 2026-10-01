package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.parser.M3uItem
import com.example.ui.theme.RedPrimary
import kotlinx.coroutines.launch

@Composable
fun SeriesDetailSheet(
    items: List<M3uItem>,
    onPlayStream: (M3uItem) -> Unit,
    onClose: () -> Unit = {}
) {
    com.example.util.KeepSystemBarsHidden()
    val context = LocalContext.current
    val seriesName = items.firstOrNull()?.seriesName ?: items.firstOrNull()?.title ?: stringResource(R.string.series_fallback)
    val groupedBySeason = remember(items) { items.groupBy { it.season ?: 1 }.toSortedMap() }
    val seasons = groupedBySeason.keys.toList()
    var selectedSeasonIndex by remember(items) { mutableStateOf(0) }

    var episodeProgressMap by remember(items) {
        mutableStateOf<Map<String, Pair<Long, Long>>>(
            items.mapNotNull { ep ->
                com.example.model.PlayerRepository.lastPositions[ep.url]?.let { ep.url to it }
            }.toMap()
        )
    }
    var lastWatchedEpisode by remember(items) { mutableStateOf<M3uItem?>(null) }

    LaunchedEffect(items) {
        try {
            val dao = com.example.model.db.AppDatabase.getDatabase(context).iptvDao()
            val mutableMap = mutableMapOf<String, Pair<Long, Long>>()
            var latestTs = -1L
            var latestEp: M3uItem? = null

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                for (ep in items) {
                    if (ep.url.isNotBlank()) {
                        val cached = com.example.model.PlayerRepository.lastPositions[ep.url]
                        val dbProg = try { dao.getProgressForUrl(ep.url) } catch (_: Exception) { null }
                        val pos = cached?.first ?: dbProg?.positionMs ?: 0L
                        val dur = cached?.second ?: dbProg?.durationMs ?: 0L
                        val ts = dbProg?.timestamp ?: (if (cached != null) System.currentTimeMillis() else 0L)
                        if (pos > 1000L) {
                            mutableMap[ep.url] = pos to dur
                            if (ts > latestTs) {
                                latestTs = ts
                                latestEp = ep
                            }
                        }
                    }
                }
            }
            episodeProgressMap = mutableMap
            lastWatchedEpisode = latestEp
            if (latestEp != null) {
                val targetSeason = latestEp!!.season ?: 1
                val seasonIdx = seasons.indexOf(targetSeason)
                if (seasonIdx >= 0) {
                    selectedSeasonIndex = seasonIdx
                }
            }
        } catch (_: Exception) {}
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val hrs = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hrs > 0) String.format("%d:%02d:%02d", hrs, mins, secs)
        else String.format("%02d:%02d", mins, secs)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (isLandscape) 0.94f else 0.82f)
            .padding(top = if (isLandscape) 4.dp else 8.dp, bottom = if (isLandscape) 8.dp else 36.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isLandscape) 6.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = seriesName,
                fontSize = if (isLandscape) 17.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val resumeEp = lastWatchedEpisode
            if (resumeEp != null) {
                val posPair = episodeProgressMap[resumeEp.url]
                val posStr = if (posPair != null) " (${formatMs(posPair.first)})" else ""
                Button(
                    onClick = { onPlayStream(resumeEp) },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Devam Et: S${resumeEp.season ?: 1} B${resumeEp.episode ?: "?"}$posStr",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(if (isLandscape) 32.dp else 40.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close_desc),
                    tint = Color.Gray
                )
            }
        }

        if (seasons.isNotEmpty()) {
            val safeSeasonIndex = selectedSeasonIndex.coerceIn(0, (seasons.size - 1).coerceAtLeast(0))
            ScrollableTabRow(
                selectedTabIndex = safeSeasonIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color.White,
                edgePadding = 16.dp,
                divider = { HorizontalDivider(color = Color.DarkGray) }
            ) {
                seasons.forEachIndexed { index, season ->
                    Tab(
                        selected = safeSeasonIndex == index,
                        onClick = { selectedSeasonIndex = index },
                        text = { Text("${stringResource(R.string.season_prefix)} $season", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            val selectedSeason = seasons.getOrNull(safeSeasonIndex)
            val episodes = groupedBySeason[selectedSeason] ?: emptyList()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(episodes.sortedBy { it.episode ?: 0 }) { episode ->
                    val epTitle = "${stringResource(R.string.episode_prefix)} ${episode.episode ?: "?"}: ${episode.title}"
                    val prog = episodeProgressMap[episode.url]
                    val hasProg = prog != null && prog.first > 1000L
                    val fraction = if (prog != null && prog.second > 0L) {
                        (prog.first.toFloat() / prog.second.toFloat()).coerceIn(0.02f, 1f)
                    } else 0f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayStream(episode) }
                            .background(
                                if (lastWatchedEpisode?.url == episode.url) RedPrimary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasProg) Icons.Default.PlayCircleFilled else Icons.Default.Tv,
                                contentDescription = null,
                                tint = if (hasProg) RedPrimary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = epTitle,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (hasProg) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (prog != null && prog.first > 1000L) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatMs(prog.first),
                                    color = Color(0xFFFFB300),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (hasProg && fraction > 0f) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { fraction },
                                color = RedPrimary,
                                trackColor = Color.DarkGray.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 40.dp)
                                    .height(3.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSheetContent(onClose: () -> Unit, onOpenSupport: () -> Unit = {}) {
    com.example.util.KeepSystemBarsHidden()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { com.example.model.db.AppDatabase.getDatabase(context) }

    var showFaqDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var showParentalDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    val orientationMode by com.example.model.SettingsManager.orientationMode.collectAsState()

    if (showCategoryDialog) {
        com.example.ui.components.CategoryManagementDialog(onDismiss = { showCategoryDialog = false })
    }
    if (showParentalDialog) {
        com.example.ui.components.ParentalControlDialog(onDismiss = { showParentalDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (isLandscape) 0.94f else 0.82f)
            .padding(horizontal = 16.dp, vertical = if (isLandscape) 6.dp else 10.dp)
            .padding(bottom = if (isLandscape) 8.dp else 28.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 34.dp else 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = if (isLandscape) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onClose, modifier = Modifier.size(if (isLandscape) 32.dp else 40.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
            }
        }
        HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = if (isLandscape) 4.dp else 8.dp))

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left Column: Display, Orientation, Parental, Update
                Column(modifier = Modifier.weight(1f)) {
                    Text("EKRAN VE GÖRÜNÜM", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 2.dp))
                    SettingsItem(
                        icon = Icons.Default.Category,
                        title = "Kategori Yönetimi (Gizle / Göster)",
                        iconTint = Color(0xFF00ACC1),
                        compact = true,
                        onClick = { showCategoryDialog = true }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(Color(0xFF161B22), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Ekran Yönü (Yatay / Dikey)", color = Color.White, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val options = listOf(0 to "Otomatik", 1 to "Dikey", 2 to "Yatay")
                            options.forEach { (mode, label) ->
                                val isSelected = orientationMode == mode
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) RedPrimary else Color(0xFF333A44)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clickable { com.example.model.SettingsManager.setOrientationMode(mode) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else Color.Gray,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_parental), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 2.dp))
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.parental_control_title),
                        iconTint = Color(0xFFE53935),
                        compact = true,
                        onClick = { showParentalDialog = true }
                    )

                    Text("SÜRÜM VE GÜNCELLEME", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 2.dp))
                    SettingsItem(
                        icon = Icons.Default.SystemUpdate,
                        title = "Güncellemeleri Kontrol Et",
                        iconTint = Color(0xFF42A5F5),
                        compact = true,
                        onClick = {
                            coroutineScope.launch {
                                com.example.model.UpdateManager.checkForUpdates(context, manual = true)
                                onClose()
                            }
                        }
                    )
                }

                // Right Column: Data updates, Share, Help
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_data), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 2.dp))
                    SettingsItem(icon = Icons.Default.Delete, title = stringResource(R.string.settings_erase_recent), iconTint = Color(0xFF42A5F5), compact = true, onClick = {
                        coroutineScope.launch {
                            db.iptvDao().clearRecentProgress()
                            Toast.makeText(context, context.getString(R.string.toast_recent_cleared), Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    })
                    SettingsItem(icon = Icons.Default.Update, title = stringResource(R.string.settings_update_movies), iconTint = Color(0xFF42A5F5), compact = true, onClick = {
                        coroutineScope.launch {
                            Toast.makeText(context, context.getString(R.string.toast_movies_updated), Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    })
                    SettingsItem(icon = Icons.Default.Update, title = stringResource(R.string.settings_update_series), iconTint = Color(0xFFAB47BC), compact = true, onClick = {
                        coroutineScope.launch {
                            Toast.makeText(context, context.getString(R.string.toast_series_updated), Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    })
                    SettingsItem(icon = Icons.Default.Update, title = stringResource(R.string.settings_update_live), iconTint = Color(0xFF66BB6A), compact = true, onClick = {
                        coroutineScope.launch {
                            Toast.makeText(context, context.getString(R.string.toast_live_updated), Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    })

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${stringResource(R.string.settings_share)} & ${stringResource(R.string.settings_help)}", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 2.dp))
                    SettingsItem(icon = Icons.Default.Share, title = stringResource(R.string.settings_share_family), iconTint = Color(0xFFBDBDBD), compact = true, onClick = {
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
                    SettingsItem(icon = Icons.Default.Help, title = stringResource(R.string.settings_faq), iconTint = Color(0xFFAB47BC), compact = true, onClick = { showFaqDialog = true })
                }
            }
        } else {
            Text("EKRAN VE GÖRÜNÜM", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            SettingsItem(icon = Icons.Default.Category, title = "Kategori Yönetimi (Gizle / Göster)", iconTint = Color(0xFF00ACC1), onClick = { showCategoryDialog = true })

            // Screen orientation selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Ekran Yönü (Yatay / Dikey)", color = Color.White, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(0 to "Otomatik", 1 to "Dikey", 2 to "Yatay")
                    options.forEach { (mode, label) ->
                        val isSelected = orientationMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) RedPrimary else Color(0xFF333A44)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { com.example.model.SettingsManager.setOrientationMode(mode) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Text(stringResource(R.string.settings_parental), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            SettingsItem(icon = Icons.Default.Lock, title = stringResource(R.string.parental_control_title), iconTint = Color(0xFFE53935), onClick = { showParentalDialog = true })

            Text("SÜRÜM VE GÜNCELLEME", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            SettingsItem(
                icon = Icons.Default.SystemUpdate,
                title = "Güncellemeleri Kontrol Et",
                iconTint = Color(0xFF42A5F5),
                onClick = {
                    coroutineScope.launch {
                        com.example.model.UpdateManager.checkForUpdates(context, manual = true)
                        onClose()
                    }
                }
            )

            Text(stringResource(R.string.settings_data), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            SettingsItem(icon = Icons.Default.Delete, title = stringResource(R.string.settings_erase_recent), iconTint = Color(0xFF42A5F5), onClick = {
                coroutineScope.launch {
                    db.iptvDao().clearRecentProgress()
                    Toast.makeText(context, context.getString(R.string.toast_recent_cleared), Toast.LENGTH_SHORT).show()
                    onClose()
                }
            })
            SettingsItem(icon = Icons.Default.Update, title = stringResource(R.string.settings_update_movies), iconTint = Color(0xFF42A5F5), onClick = {
                coroutineScope.launch {
                    Toast.makeText(context, context.getString(R.string.toast_movies_updated), Toast.LENGTH_SHORT).show()
                    onClose()
                }
            })
            SettingsItem(icon = Icons.Default.Update, title = stringResource(R.string.settings_update_series), iconTint = Color(0xFFAB47BC), onClick = {
                coroutineScope.launch {
                    Toast.makeText(context, context.getString(R.string.toast_series_updated), Toast.LENGTH_SHORT).show()
                    onClose()
                }
            })
            SettingsItem(icon = Icons.Default.Update, title = stringResource(R.string.settings_update_live), iconTint = Color(0xFF66BB6A), onClick = {
                coroutineScope.launch {
                    Toast.makeText(context, context.getString(R.string.toast_live_updated), Toast.LENGTH_SHORT).show()
                    onClose()
                }
            })

            Text(stringResource(R.string.settings_share), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            SettingsItem(icon = Icons.Default.Share, title = stringResource(R.string.settings_share_family), iconTint = Color(0xFFBDBDBD), onClick = {
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
            SettingsItem(icon = Icons.Default.Help, title = stringResource(R.string.settings_faq), iconTint = Color(0xFFAB47BC), onClick = { showFaqDialog = true })
        }
    }

    if (showFaqDialog) {
        AlertDialog(
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
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text(stringResource(R.string.rate_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rate_desc), color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= selectedStars) Icons.Default.Star else Icons.Default.StarOutline,
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
        AlertDialog(
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
fun SettingsItem(icon: ImageVector, title: String, iconTint: Color, onClick: () -> Unit = {}, compact: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 7.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 28.dp else 32.dp)
                .background(iconTint.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(if (compact) 17.dp else 20.dp))
        }
        Spacer(modifier = Modifier.width(if (compact) 12.dp else 16.dp))
        Text(text = title, color = Color.White, fontSize = if (compact) 13.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsSheet(
    onClose: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {}
) {
    com.example.util.KeepSystemBarsHidden()
    val context = LocalContext.current
    val currentLang by com.example.model.AppLanguageManager.currentLanguage.collectAsState()
    val db = remember { com.example.model.db.AppDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()
    var currentUser by remember { mutableStateOf<com.example.model.db.UserEntity?>(null) }
    var showSupportInline by remember { mutableStateOf(false) }

    var showFaqDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        currentUser = db.iptvDao().getFirstUser()
    }

    if (showSupportInline) {
        SupportTicketsSheet(onClose = { showSupportInline = false })
    } else {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscapeConfig = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (isLandscapeConfig) 0.95f else 0.84f)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (isLandscapeConfig) 6.dp else 10.dp,
                    bottom = if (isLandscapeConfig) 8.dp else 32.dp
                )
        ) {
            val orientationModeSheet by com.example.model.SettingsManager.orientationMode.collectAsState()
            val isLandscape = orientationModeSheet != 1 &&
                maxWidth > 520.dp &&
                isLandscapeConfig

            if (isLandscape) {
                // Horizontal / Landscape Layout: Full header + 2 compact columns fitting on a single screen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.personalization_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
                        }
                    }
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Left Column: Profile Card, Language, Screen Orientation & Category
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            // Connected Google Account Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF263238), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4285F4),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.google_account_connected), fontSize = 10.sp, color = Color(0xFF90CAF9))
                                    Text(
                                        currentUser?.email ?: currentUser?.name ?: "Giriş Yapılmadı",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.language_selection),
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (currentLang == "tr") RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (currentLang == "tr") RedPrimary else Color(0xFF333A44)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clickable { com.example.model.AppLanguageManager.setLanguage(context, "tr") }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = currentLang == "tr",
                                            onClick = { com.example.model.AppLanguageManager.setLanguage(context, "tr") },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Türkçe", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (currentLang == "en") RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (currentLang == "en") RedPrimary else Color(0xFF333A44)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clickable { com.example.model.AppLanguageManager.setLanguage(context, "en") }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = currentLang == "en",
                                            onClick = { com.example.model.AppLanguageManager.setLanguage(context, "en") },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("English", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Ekran Yönü ve Kategori Yönetimi",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            var showCategoryDialogProfile by remember { mutableStateOf(false) }
                            val orientationModeProfile by com.example.model.SettingsManager.orientationMode.collectAsState()

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E232A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333A44)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clickable { showCategoryDialogProfile = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Category, contentDescription = null, tint = Color(0xFF00ACC1), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Kategoriler", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            if (showCategoryDialogProfile) {
                                com.example.ui.components.CategoryManagementDialog(onDismiss = { showCategoryDialogProfile = false })
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val options = listOf(0 to "Oto", 1 to "Dikey", 2 to "Yatay")
                                options.forEach { (mode, label) ->
                                    val isSelected = orientationModeProfile == mode
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) RedPrimary else Color(0xFF333A44)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clickable { com.example.model.SettingsManager.setOrientationMode(mode) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(label, color = if (isSelected) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }

                        // Right Column: Version Update, Share, FAQ, Support & Account Management Actions
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "SÜRÜM VE MENÜLER",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            // Version Update
                            SettingsItem(
                                icon = Icons.Default.SystemUpdate,
                                title = "Güncellemeleri Kontrol Et",
                                iconTint = Color(0xFF42A5F5),
                                compact = true,
                                onClick = {
                                    coroutineScope.launch {
                                        com.example.model.UpdateManager.checkForUpdates(context, manual = true)
                                    }
                                }
                            )

                            // Share & FAQ
                            SettingsItem(
                                icon = Icons.Default.Share,
                                title = stringResource(R.string.settings_share_family),
                                iconTint = Color(0xFFBDBDBD),
                                compact = true,
                                onClick = {
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_share_family)))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )

                            // FAQ
                            SettingsItem(
                                icon = Icons.Default.Help,
                                title = stringResource(R.string.settings_faq),
                                iconTint = Color(0xFFAB47BC),
                                compact = true,
                                onClick = { showFaqDialog = true }
                            )

                            // Support
                            SettingsItem(
                                icon = Icons.Default.SupportAgent,
                                title = stringResource(R.string.settings_support),
                                iconTint = Color(0xFF42A5F5),
                                compact = true,
                                onClick = {
                                    val devId = com.example.model.DeviceManager.getDeviceId()
                                    if (devId.isBlank()) {
                                        Toast.makeText(context, "Cihaz kimliği bulunamadı, lütfen önce cihazınızı eşleştirin.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onOpenSupport()
                                        showSupportInline = true
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.account_management),
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Sign In or Sign Out
                                if (currentUser == null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .background(Color(0xFF1E232A), RoundedCornerShape(8.dp))
                                            .clickable {
                                                onNavigateToAuth()
                                            }
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Login, contentDescription = null, tint = Color(0xFF42A5F5), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.sign_in_account), color = Color.White, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .background(Color(0xFF1E232A), RoundedCornerShape(8.dp))
                                            .clickable {
                                                coroutineScope.launch {
                                                    db.iptvDao().clearUsers()
                                                    Toast.makeText(context, context.getString(R.string.sign_out_account), Toast.LENGTH_SHORT).show()
                                                    onNavigateToAuth()
                                                }
                                            }
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.sign_out_account), color = Color.White, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }

                                // Remove Account
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .background(Color(0xFF2E1C1C), RoundedCornerShape(8.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                db.iptvDao().clearUsers()
                                                db.iptvDao().clearRecentProgress()
                                                Toast.makeText(context, context.getString(R.string.account_removed_toast), Toast.LENGTH_SHORT).show()
                                                onNavigateToAuth()
                                            }
                                        }
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.remove_account), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            } else {
            // Portrait Layout: Single vertical column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.personalization_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                Text(stringResource(R.string.language_selection), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                    com.example.model.AppLanguageManager.setLanguage(context, "tr")
                }.padding(vertical = 6.dp)) {
                    RadioButton(selected = currentLang == "tr", onClick = { com.example.model.AppLanguageManager.setLanguage(context, "tr") })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Türkçe", color = Color.White)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                    com.example.model.AppLanguageManager.setLanguage(context, "en")
                }.padding(vertical = 6.dp)) {
                    RadioButton(selected = currentLang == "en", onClick = { com.example.model.AppLanguageManager.setLanguage(context, "en") })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("English", color = Color.White)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Ekran Yönü ve Kategori Yönetimi",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                var showCategoryDialogPortrait by remember { mutableStateOf(false) }
                val orientationModePortrait by com.example.model.SettingsManager.orientationMode.collectAsState()

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E232A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333A44)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCategoryDialogPortrait = true }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = Color(0xFF00ACC1), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kategoriler", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (showCategoryDialogPortrait) {
                    com.example.ui.components.CategoryManagementDialog(onDismiss = { showCategoryDialogPortrait = false })
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(0 to "Oto", 1 to "Dikey", 2 to "Yatay")
                    options.forEach { (mode, label) ->
                        val isSelected = orientationModePortrait == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) RedPrimary else Color(0xFF333A44)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { com.example.model.SettingsManager.setOrientationMode(mode) }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(label, color = if (isSelected) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                Text("SÜRÜM VE MENÜLER", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                SettingsItem(
                    icon = Icons.Default.SystemUpdate,
                    title = "Güncellemeleri Kontrol Et",
                    iconTint = Color(0xFF42A5F5),
                    onClick = {
                        coroutineScope.launch {
                            com.example.model.UpdateManager.checkForUpdates(context, manual = true)
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.settings_share_family),
                    iconTint = Color(0xFFBDBDBD),
                    onClick = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_share_family)))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Help,
                    title = stringResource(R.string.settings_faq),
                    iconTint = Color(0xFFAB47BC),
                    onClick = { showFaqDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.SupportAgent,
                    title = stringResource(R.string.settings_support),
                    iconTint = Color(0xFF42A5F5),
                    onClick = {
                        val devId = com.example.model.DeviceManager.getDeviceId()
                        if (devId.isBlank()) {
                            Toast.makeText(context, "Cihaz kimliği bulunamadı, lütfen önce cihazınızı eşleştirin.", Toast.LENGTH_SHORT).show()
                        } else {
                            onOpenSupport()
                            showSupportInline = true
                        }
                    }
                )

                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                Text(stringResource(R.string.account_management), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

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
                        Text(stringResource(R.string.google_account_connected), fontSize = 11.sp, color = Color.Gray)
                        Text(currentUser?.email ?: currentUser?.name ?: "Giriş Yapılmadı", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (currentUser == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigateToAuth()
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, tint = Color(0xFF42A5F5))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.sign_in_account), color = Color.White, fontSize = 15.sp)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    db.iptvDao().clearUsers()
                                    Toast.makeText(context, context.getString(R.string.sign_out_account), Toast.LENGTH_SHORT).show()
                                    onNavigateToAuth()
                                }
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFFA726))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.sign_out_account), color = Color.White, fontSize = 15.sp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                db.iptvDao().clearUsers()
                                db.iptvDao().clearRecentProgress()
                                Toast.makeText(context, context.getString(R.string.account_removed_toast), Toast.LENGTH_SHORT).show()
                                onNavigateToAuth()
                            }
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color(0xFFEF5350))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.remove_account), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    }

    // Dialogs inside Profile Settings
    if (showFaqDialog) {
        AlertDialog(
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
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text(stringResource(R.string.rate_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rate_desc), color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= selectedStars) Icons.Default.Star else Icons.Default.StarOutline,
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
}

@Composable
fun SupportTicketsSheet(onClose: () -> Unit) {
    com.example.util.KeepSystemBarsHidden()
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val tickets by com.example.model.SupportRepository.tickets.collectAsState()
    val isSyncing by com.example.model.SupportRepository.isSyncing.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    var newMessage by remember { mutableStateOf("") }
    var showNewTicketForm by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        com.example.model.SupportRepository.syncTicketsFromOdoo()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (isLandscape) 0.95f else 0.82f)
            .padding(horizontal = 16.dp, vertical = if (isLandscape) 4.dp else 10.dp)
            .padding(bottom = if (isLandscape) 6.dp else 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 36.dp else 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = Color(0xFF42A5F5),
                    modifier = Modifier.size(if (isLandscape) 22.dp else 28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.support_tickets_title),
                    fontSize = if (isLandscape) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isSyncing) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF42A5F5)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLandscape && !showNewTicketForm) {
                    Button(
                        onClick = { showNewTicketForm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ticket_new_btn), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(if (isLandscape) 32.dp else 40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
                }
            }
        }
        HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = if (isLandscape) 4.dp else 8.dp))

        if (showNewTicketForm) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = if (isLandscape) 2.dp else 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(if (isLandscape) 10.dp else 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.ticket_new_btn),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = if (isLandscape) 13.sp else 14.sp
                        )
                        if (isLandscape) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { showNewTicketForm = false },
                                    enabled = !isSubmitting,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(stringResource(R.string.close_desc), color = Color.Gray, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (newTitle.isNotBlank() && newMessage.isNotBlank()) {
                                            isSubmitting = true
                                            scope.launch {
                                                val result = com.example.model.SupportRepository.createTicketSuspend(newTitle.trim(), null, newMessage.trim())
                                                isSubmitting = false
                                                if (result.isSuccess) {
                                                    newTitle = ""
                                                    newMessage = ""
                                                    showNewTicketForm = false
                                                    Toast.makeText(context, context.getString(R.string.report_sent), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val err = result.exceptionOrNull()?.message ?: "Bilinmeyen hata"
                                                    Toast.makeText(context, "Destek talebi iletilemedi: $err", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "Lütfen başlık ve mesaj giriniz.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                                    enabled = !isSubmitting,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(stringResource(R.string.report_send), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 8.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text(stringResource(R.string.ticket_title_label), color = Color.Gray, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF42A5F5), unfocusedBorderColor = Color.DarkGray),
                        enabled = !isSubmitting
                    )
                    Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 8.dp))
                    OutlinedTextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        placeholder = { Text(stringResource(R.string.ticket_msg_label), color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 68.dp else 90.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF42A5F5), unfocusedBorderColor = Color.DarkGray),
                        enabled = !isSubmitting
                    )
                    if (!isLandscape) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = { showNewTicketForm = false },
                                enabled = !isSubmitting
                            ) {
                                Text(stringResource(R.string.close_desc), color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newTitle.isNotBlank() && newMessage.isNotBlank()) {
                                        isSubmitting = true
                                        scope.launch {
                                            val result = com.example.model.SupportRepository.createTicketSuspend(newTitle.trim(), null, newMessage.trim())
                                            isSubmitting = false
                                            if (result.isSuccess) {
                                                newTitle = ""
                                                newMessage = ""
                                                showNewTicketForm = false
                                                Toast.makeText(context, context.getString(R.string.report_sent), Toast.LENGTH_SHORT).show()
                                            } else {
                                                val err = result.exceptionOrNull()?.message ?: "Bilinmeyen hata"
                                                Toast.makeText(context, "Destek talebi iletilemedi: $err", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Lütfen başlık ve mesaj giriniz.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                                enabled = !isSubmitting
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(stringResource(R.string.report_send), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else if (!isLandscape) {
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

        if (!isLandscape || !showNewTicketForm) {
            Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

            if (tickets.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(if (isLandscape) 36.dp else 48.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Henüz bir destek talebiniz bulunmuyor.", color = Color.Gray, fontSize = if (isLandscape) 13.sp else 14.sp)
                        Text("Yeni talep oluşturmak için yukarıdaki butonu kullanın.", color = Color.DarkGray, fontSize = if (isLandscape) 11.sp else 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
                ) {
                    items(tickets.size) { index ->
                        val ticket = tickets[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(if (isLandscape) 10.dp else 12.dp)) {
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
}
