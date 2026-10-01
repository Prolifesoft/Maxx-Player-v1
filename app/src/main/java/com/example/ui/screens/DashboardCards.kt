package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.R
import com.example.parser.M3uItem
import java.util.Locale

@Composable
fun HeroBanner(
    item: M3uItem,
    onClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }

    val isSeries = item.type == com.example.parser.ItemType.SERIES ||
        !item.seriesName.isNullOrBlank() ||
        Regex("(?i)\\b(s\\d{1,2}\\s*e\\d{1,3}|sezon\\s*\\d+|b[öo]l[üu]m\\s*\\d+)\\b").containsMatchIn(item.title)
    val queryTitle = if (isSeries) {
        item.seriesName?.takeIf { it.isNotBlank() } ?: item.title
    } else {
        item.title
    }

    var backdropList by remember(item.url, item.title) { mutableStateOf<List<String>>(emptyList()) }
    var currentBackdropIndex by remember(item.url, item.title) { mutableIntStateOf(0) }

    LaunchedEffect(queryTitle, isSeries, item.url) {
        currentBackdropIndex = 0
        val detail = fetchTmdbFullDetail(query = queryTitle, isSeries = isSeries)
        val itemBackdrops = detail?.backdropUrls?.filter { it.isNotBlank() }.orEmpty()
        if (itemBackdrops.size > 1) {
            backdropList = itemBackdrops
        } else {
            val fallbackBackdrops = fetchTmdbFallbackBackdrops("969681")
            backdropList = when {
                itemBackdrops.isNotEmpty() && fallbackBackdrops.isNotEmpty() -> (itemBackdrops + fallbackBackdrops).distinct()
                fallbackBackdrops.isNotEmpty() -> fallbackBackdrops
                itemBackdrops.isNotEmpty() -> itemBackdrops
                !item.logo.isNullOrBlank() -> listOf(item.logo)
                else -> emptyList()
            }
        }
    }

    LaunchedEffect(backdropList) {
        currentBackdropIndex = 0
        if (backdropList.size > 1) {
            while (true) {
                kotlinx.coroutines.delay(3500L)
                currentBackdropIndex = (currentBackdropIndex + 1) % backdropList.size
            }
        }
    }

    val activeBackdropUrl = backdropList.getOrNull(currentBackdropIndex) ?: item.logo

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(onClick = onClick)
            .background(Color(0xFF0F0F17))
    ) {
        androidx.compose.animation.Crossfade(
            targetState = activeBackdropUrl,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 700),
            modifier = Modifier.fillMaxSize(),
            label = "hero_backdrop_slide"
        ) { bgUrl ->
            if (!bgUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(bgUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.62f),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.48f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
        val isFavorite = favoriteUrls.contains(item.url)
        val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
        val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
        val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
        val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
        var showUnlockDialog by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.play), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = { com.example.model.FavoritesManager.toggleFavorite(item.url) },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) Color.Red else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            if (!hasPin) {
                                Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                            } else if (isLocked) {
                                showUnlockDialog = true
                            } else {
                                com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                                Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Kilit",
                            tint = if (isLocked) Color(0xFFEF5350) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            if (onInfoClick != null) {
                                onInfoClick()
                            } else {
                                showInfoDialog = true
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.info_button_desc),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!item.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .height(160.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (showUnlockDialog) {
            com.example.ui.components.PinUnlockDialog(
                onUnlock = {
                    com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                    showUnlockDialog = false
                    Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showUnlockDialog = false }
            )
        }

        if (showInfoDialog) {
            ContentInfoDialog(
                item = item,
                onDismiss = { showInfoDialog = false },
                onPlay = {
                    showInfoDialog = false
                    onClick()
                }
            )
        }
    }
}

@Composable
fun MovieCard(
    item: M3uItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(122.dp),
    badgeText: String? = null,
    onInfoClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
    val isFavorite = favoriteUrls.contains(item.url)
    val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
    val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
    val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
    val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B202A))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(Color(0xFF232A38)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.logo.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.logo)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = if (item.type == com.example.parser.ItemType.LIVE) ContentScale.Fit else ContentScale.Crop,
                        error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                    )
                } else {
                    Icon(
                        if (item.type == com.example.parser.ItemType.LIVE) Icons.Default.LiveTv else Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                }

                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            color = Color(0xFFFFB300),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { com.example.model.FavoritesManager.toggleFavorite(item.url) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) Color.Red else Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!hasPin) {
                                Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                            } else if (isLocked) {
                                showUnlockDialog = true
                            } else {
                                com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                                Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Kilit",
                            tint = if (isLocked) Color(0xFFEF5350) else Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (onInfoClick != null) {
                                onInfoClick()
                            } else {
                                showInfoDialog = true
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.info_button_desc),
                            tint = Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }

    if (showUnlockDialog) {
        com.example.ui.components.PinUnlockDialog(
            onUnlock = {
                com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                showUnlockDialog = false
                Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showUnlockDialog = false }
        )
    }

    if (showInfoDialog) {
        ContentInfoDialog(
            item = item,
            onDismiss = { showInfoDialog = false },
            onPlay = {
                showInfoDialog = false
                onClick()
            }
        )
    }
}

@Composable
fun ChannelCard(
    item: M3uItem,
    onClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
    val isFavorite = favoriteUrls.contains(item.url)
    val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
    val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
    val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
    val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B202A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!item.logo.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.logo)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Fit,
                        error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                    )
                } else {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { com.example.model.FavoritesManager.toggleFavorite(item.url) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) Color.Red else Color.LightGray,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (!hasPin) {
                            Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                        } else if (isLocked) {
                            showUnlockDialog = true
                        } else {
                            com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                            Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Kilit",
                        tint = if (isLocked) Color(0xFFEF5350) else Color.LightGray,
                        modifier = Modifier.size(15.dp)
                    )
                }

                if (item.type != com.example.parser.ItemType.LIVE || onInfoClick != null) {
                    IconButton(
                        onClick = {
                            if (onInfoClick != null) {
                                onInfoClick()
                            } else {
                                showInfoDialog = true
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.info_button_desc),
                            tint = Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }

    if (showUnlockDialog) {
        com.example.ui.components.PinUnlockDialog(
            onUnlock = {
                com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                showUnlockDialog = false
                Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showUnlockDialog = false }
        )
    }

    if (showInfoDialog) {
        ContentInfoDialog(
            item = item,
            onDismiss = { showInfoDialog = false },
            onPlay = {
                showInfoDialog = false
                onClick()
            }
        )
    }
}

@Composable
fun SeriesCard(
    seriesName: String,
    item: M3uItem,
    episodeCount: Int,
    onClick: () -> Unit,
    episodes: List<M3uItem> = emptyList(),
    onPlayEpisode: ((M3uItem) -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
    val isFavorite = favoriteUrls.contains(item.url)
    val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
    val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
    val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
    val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B202A))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            ) {
                if (!item.logo.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.logo)
                            .crossfade(true)
                            .build(),
                        contentDescription = seriesName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF232A38)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Tv,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color.Gray.copy(alpha = 0.5f)
                        )
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "$episodeCount Bölüm",
                        fontSize = 9.sp,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = seriesName,
                    fontSize = 11.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { com.example.model.FavoritesManager.toggleFavorite(item.url) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) Color.Red else Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!hasPin) {
                                Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                            } else if (isLocked) {
                                showUnlockDialog = true
                            } else {
                                com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                                Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Kilit",
                            tint = if (isLocked) Color(0xFFEF5350) else Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (onInfoClick != null) {
                                onInfoClick()
                            } else {
                                showInfoDialog = true
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.info_button_desc),
                            tint = Color.LightGray,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }

    if (showUnlockDialog) {
        com.example.ui.components.PinUnlockDialog(
            onUnlock = {
                com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                showUnlockDialog = false
                Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showUnlockDialog = false }
        )
    }

    if (showInfoDialog) {
        ContentInfoDialog(
            item = item.copy(
                type = com.example.parser.ItemType.SERIES,
                seriesName = seriesName.ifBlank { item.seriesName ?: item.title }
            ),
            seriesEpisodes = episodes,
            onDismiss = { showInfoDialog = false },
            onPlay = { targetEpisode ->
                showInfoDialog = false
                if (targetEpisode != null && onPlayEpisode != null) {
                    onPlayEpisode(targetEpisode)
                } else {
                    onClick()
                }
            }
        )
    }
}

@Composable
fun ProgressCard(progress: com.example.model.db.PlaybackProgressEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(90.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!progress.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(progress.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = progress.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
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
                    .padding(6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = progress.title,
                        fontSize = 11.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs.toFloat()) else 0f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.DarkGray
                    )
                }
            }
        }
    }
}

@Composable
fun MatchFixtureCard(fixture: MatchFixture, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        border = BorderStroke(1.dp, if (fixture.isLive) Color(0xFFE50914) else Color(0xFF2E2E42))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fixture.league,
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (fixture.isLive) {
                    Surface(
                        color = Color(0xFFE50914),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "CANLI",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = fixture.dateText,
                        fontSize = 10.sp,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    if (fixture.homeLogo.isNotBlank()) {
                        AsyncImage(
                            model = fixture.homeLogo,
                            contentDescription = fixture.homeTeam,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fixture.homeTeam,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                if (fixture.homeScore.isNotBlank() && fixture.awayScore.isNotBlank()) {
                    Text(
                        text = "${fixture.homeScore} - ${fixture.awayScore}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (fixture.isLive) Color(0xFFFF5252) else Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else {
                    Text(
                        text = "VS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    if (fixture.awayLogo.isNotBlank()) {
                        AsyncImage(
                            model = fixture.awayLogo,
                            contentDescription = fixture.awayTeam,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fixture.awayTeam,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A2A3E), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = fixture.broadcastChannel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MatchFixtureDetailedCard(fixture: MatchFixture, onWatchClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onWatchClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B222D)),
        border = BorderStroke(1.dp, if (fixture.isLive) Color(0xFFE50914) else Color(0xFF2C384A))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SportsSoccer,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = fixture.league,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                }

                if (fixture.isLive) {
                    Surface(
                        color = Color(0xFFE50914),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CANLI",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = fixture.dateText,
                        fontSize = 11.sp,
                        color = Color(0xFFFFCA28),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Home Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (fixture.homeLogo.isNotBlank()) {
                        AsyncImage(
                            model = fixture.homeLogo,
                            contentDescription = fixture.homeTeam,
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = fixture.homeTeam,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Score or VS
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (fixture.homeScore.isNotBlank() && fixture.awayScore.isNotBlank()) {
                        Text(
                            text = "${fixture.homeScore} - ${fixture.awayScore}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (fixture.isLive) Color(0xFFFF5252) else Color.White
                        )
                    } else {
                        Text(
                            text = if (fixture.isLive) "CANLI" else "VS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }

                // Away Team
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = fixture.awayTeam,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    if (fixture.awayLogo.isNotBlank()) {
                        AsyncImage(
                            model = fixture.awayLogo,
                            contentDescription = fixture.awayTeam,
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = Color(0xFF42A5F5),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Yayın: ${fixture.broadcastChannel}",
                        fontSize = 11.sp,
                        color = Color(0xFF90CAF9),
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onWatchClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (fixture.isLive) Color(0xFFE50914) else MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (fixture.isLive) "Yayını İzle" else "Kanala Git", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ImdbCard(
    item: ImdbUpcomingItem,
    isNotified: Boolean,
    onNotifyToggle: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A28)),
        border = BorderStroke(1.dp, Color(0xFF2A2A3E))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )

                if (item.trailerKey != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${item.trailerKey}"))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "YouTube uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Fragman",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Surface(
                    color = if (item.type == "Film") Color(0xFF1E88E5) else Color(0xFF8E24AA),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = item.type,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = Color(0xFFF5C518),
                    shape = RoundedCornerShape(topEnd = 8.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = item.imdbRating,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.releaseDate,
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onNotifyToggle,
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNotified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isNotified) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isNotified) "Haber Ver" else "Haber Ver",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                color = if (item.trailerKey != null) Color(0xFFE50914) else Color.DarkGray,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                if (item.trailerKey != null) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${item.trailerKey}"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "YouTube uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Fragman bulunamadı", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.trailerKey != null) Icons.Default.PlayArrow else Icons.Default.PlayDisabled,
                            contentDescription = if (item.trailerKey != null) "Fragman İzle" else "Fragman Yok",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContentInfoDialog(
    item: M3uItem,
    onDismiss: () -> Unit,
    seriesEpisodes: List<M3uItem> = emptyList(),
    onPlay: ((M3uItem?) -> Unit)? = null
) {
    val isSeries = item.type == com.example.parser.ItemType.SERIES
    val displayTitle = if (isSeries) {
        item.seriesName?.takeIf { it.isNotBlank() } ?: item.title
    } else {
        item.title
    }
    val queryTitle = if (isSeries) {
        item.seriesName?.takeIf { it.isNotBlank() } ?: item.title
    } else {
        item.title
    }
    ContentInfoDialog(
        title = displayTitle,
        posterUrl = item.logo,
        isSeries = isSeries,
        onDismiss = onDismiss,
        searchQuery = queryTitle,
        mediaUrl = item.url,
        seriesEpisodes = seriesEpisodes,
        onPlay = onPlay
    )
}

@Composable
fun ContentInfoDialog(
    title: String,
    posterUrl: String?,
    isSeries: Boolean,
    onDismiss: () -> Unit,
    searchQuery: String = title,
    tmdbId: String? = null,
    initialOverview: String? = null,
    initialRating: String? = null,
    initialReleaseDate: String? = null,
    genres: List<String> = emptyList(),
    mediaUrl: String? = null,
    seriesEpisodes: List<M3uItem> = emptyList(),
    onPlay: ((M3uItem?) -> Unit)? = null
) {
    val context = LocalContext.current
    var tmdbDetail by remember { mutableStateOf<TmdbFullDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var resumeProgress by remember { mutableStateOf<com.example.model.db.PlaybackProgressEntity?>(null) }
    var matchedResumeEpisode by remember { mutableStateOf<M3uItem?>(null) }
    var isOverviewExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, isSeries, tmdbId) {
        isLoading = true
        tmdbDetail = fetchTmdbFullDetail(
            query = searchQuery,
            isSeries = isSeries,
            tmdbId = tmdbId
        )
        isLoading = false
    }

    LaunchedEffect(mediaUrl, seriesEpisodes) {
        try {
            val dao = com.example.model.db.AppDatabase.getDatabase(context).iptvDao()
            var foundProgress: com.example.model.db.PlaybackProgressEntity? = null
            var foundEp: M3uItem? = null

            if (!mediaUrl.isNullOrBlank()) {
                val direct = dao.getProgressForUrl(mediaUrl)
                if (direct != null && direct.positionMs > 0L && direct.durationMs > 0L) {
                    foundProgress = direct
                }
            }

            if (foundProgress == null && seriesEpisodes.isNotEmpty()) {
                var latestTimestamp = -1L
                for (ep in seriesEpisodes) {
                    if (ep.url.isNotBlank()) {
                        val p = dao.getProgressForUrl(ep.url)
                        if (p != null && p.positionMs > 0L && p.durationMs > 0L && p.timestamp > latestTimestamp) {
                            latestTimestamp = p.timestamp
                            foundProgress = p
                            foundEp = ep
                        }
                    }
                }
            }

            resumeProgress = foundProgress
            matchedResumeEpisode = foundEp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val backdropList = remember(tmdbDetail, posterUrl) {
        val fromTmdb = tmdbDetail?.backdropUrls?.filter { it.isNotBlank() }.orEmpty()
        when {
            fromTmdb.isNotEmpty() -> fromTmdb
            !tmdbDetail?.backdropUrl.isNullOrBlank() -> listOf(tmdbDetail!!.backdropUrl!!)
            !tmdbDetail?.posterUrl.isNullOrBlank() -> listOf(tmdbDetail!!.posterUrl!!)
            !posterUrl.isNullOrBlank() -> listOf(posterUrl)
            else -> emptyList()
        }
    }
    var currentBackdropIndex by remember(backdropList) { mutableIntStateOf(0) }
    LaunchedEffect(backdropList) {
        currentBackdropIndex = 0
        if (backdropList.size > 1) {
            while (true) {
                kotlinx.coroutines.delay(3500L)
                currentBackdropIndex = (currentBackdropIndex + 1) % backdropList.size
            }
        }
    }
    val backdropOrPoster = backdropList.getOrNull(currentBackdropIndex) ?: tmdbDetail?.backdropUrl ?: tmdbDetail?.posterUrl ?: posterUrl
    val ratingText = tmdbDetail?.rating?.takeIf { it > 0.0 }?.let {
        String.format(Locale.US, "%.1f", it)
    } ?: initialRating?.takeIf { it.isNotBlank() }
    val releaseDateText = tmdbDetail?.releaseDate?.takeIf { it.isNotBlank() }
        ?: initialReleaseDate?.takeIf { it.isNotBlank() }
    val runtimeMinutes = tmdbDetail?.runtimeMinutes
    val directors = tmdbDetail?.directors ?: emptyList()
    val castList = tmdbDetail?.cast ?: emptyList()
    val overviewText = tmdbDetail?.overview?.takeIf { it.isNotBlank() }
        ?: initialOverview?.takeIf { it.isNotBlank() }
        ?: if (isLoading) stringResource(R.string.info_loading) else stringResource(R.string.no_overview)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.example.util.KeepSystemBarsHidden()
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = if (isLandscape) 820.dp else 720.dp)
                .padding(vertical = if (isLandscape) 6.dp else 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10131C)),
            border = BorderStroke(1.dp, Color(0xFF282F42))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (isLandscape) 145.dp else 240.dp)
                ) {
                    androidx.compose.animation.Crossfade(
                        targetState = backdropOrPoster,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 700),
                        modifier = Modifier.matchParentSize(),
                        label = "content_info_backdrop_slide"
                    ) { bgUrl ->
                        if (!bgUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(bgUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.55f),
                                contentScale = ContentScale.Crop,
                                error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF10131C).copy(alpha = 0.55f),
                                        Color(0xFF10131C).copy(alpha = 0.82f),
                                        Color(0xFF10131C)
                                    )
                                )
                            )
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(if (isLandscape) 6.dp else 10.dp)
                            .size(if (isLandscape) 30.dp else 34.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_desc),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                top = if (isLandscape) 10.dp else 16.dp,
                                end = 48.dp,
                                bottom = if (isLandscape) 6.dp else 12.dp
                            )
                    ) {
                        // Top-left metadata row: Rating, Release Date, Runtime
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!ratingText.isNullOrBlank()) {
                                Surface(
                                    color = Color(0xFFF5C518),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = ratingText,
                                            color = Color.Black,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            if (!releaseDateText.isNullOrBlank()) {
                                Text(
                                    text = stringResource(R.string.info_release_date, releaseDateText),
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (runtimeMinutes != null && runtimeMinutes > 0) {
                                Text(
                                    text = if (isSeries) {
                                        stringResource(R.string.info_runtime_series, runtimeMinutes)
                                    } else {
                                        stringResource(R.string.info_runtime_movie, runtimeMinutes)
                                    },
                                    color = Color(0xFFFFCA28),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 10.dp))

                        Text(
                            text = tmdbDetail?.title?.takeIf { it.isNotBlank() } ?: title,
                            color = Color.White,
                            fontSize = if (isLandscape) 17.sp else 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = if (isLandscape) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.imdb_genres_label, genres.joinToString(", ")),
                                color = Color(0xFF90CAF9),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (directors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(if (isLandscape) 2.dp else 6.dp))
                            val joinedDirectors = directors.joinToString(", ")
                            Text(
                                text = if (isSeries) {
                                    stringResource(R.string.info_creator, joinedDirectors)
                                } else {
                                    stringResource(R.string.info_director, joinedDirectors)
                                },
                                color = Color(0xFFCFD8DC),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (castList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val summaryNames = castList.take(5).joinToString(", ") { it.name }
                            Text(
                                text = stringResource(R.string.info_cast_summary, summaryNames),
                                color = Color(0xFFB0BEC5),
                                fontSize = 11.5.sp,
                                maxLines = if (isLandscape) 1 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

                        Text(
                            text = overviewText,
                            color = Color(0xFFD5DCE2),
                            fontSize = if (isLandscape) 12.sp else 13.sp,
                            lineHeight = if (isLandscape) 16.sp else 18.sp,
                            maxLines = if (isOverviewExpanded) 15 else if (isLandscape) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { isOverviewExpanded = !isOverviewExpanded }
                        )

                        // Continue Watching pill button + thin progress bar + % (ONLY if saved resume progress exists)
                        val prog = resumeProgress
                        if (prog != null && prog.positionMs > 0L && prog.durationMs > 0L) {
                            val progressRatio = (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                            val progressPercent = (progressRatio * 100f).toInt().coerceIn(1, 100)

                            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 14.dp))

                            if (isLandscape) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (onPlay != null) {
                                                onPlay(matchedResumeEpisode)
                                            } else {
                                                onDismiss()
                                            }
                                        },
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.5.dp, Color(0xFFE50914)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.65f),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.continue_watching),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        LinearProgressIndicator(
                                            progress = { progressRatio },
                                            modifier = Modifier
                                                .width(140.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = Color(0xFFE50914),
                                            trackColor = Color.White.copy(alpha = 0.25f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "%$progressPercent",
                                            color = Color(0xFFE0E0E0),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (onPlay != null) {
                                                onPlay(matchedResumeEpisode)
                                            } else {
                                                onDismiss()
                                            }
                                        },
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.5.dp, Color(0xFFE50914)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.65f),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.continue_watching),
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { progressRatio },
                                            modifier = Modifier
                                                .width(150.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = Color(0xFFE50914),
                                            trackColor = Color.White.copy(alpha = 0.25f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "%$progressPercent",
                                            color = Color(0xFFE0E0E0),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isLandscape) 6.dp else 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Cast section with photos
                if (castList.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = if (isLandscape) 4.dp else 10.dp
                            )
                    ) {
                        Text(
                            text = stringResource(R.string.info_cast_section_title),
                            color = Color.White,
                            fontSize = if (isLandscape) 12.5.sp else 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)
                        ) {
                            items(castList) { member ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(if (isLandscape) 68.dp else 76.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isLandscape) 48.dp else 64.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF232A38)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!member.photoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(member.photoUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = member.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = member.name,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(if (isLandscape) 24.dp else 30.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Text(
                                        text = member.name,
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (member.character.isNotBlank()) {
                                        Text(
                                            text = member.character,
                                            color = Color.Gray,
                                            fontSize = 9.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
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

@Composable
fun ImdbDetailDialog(
    item: ImdbUpcomingItem,
    isNotified: Boolean,
    onNotifyToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isSeries = item.type.equals("Dizi", ignoreCase = true) ||
        item.type.equals("TV Shows", ignoreCase = true) ||
        item.type.equals("TV Series", ignoreCase = true)

    var tmdbDetail by remember { mutableStateOf<TmdbFullDetail?>(null) }
    var isOverviewExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(item.tmdbId, item.title, isSeries) {
        tmdbDetail = fetchTmdbFullDetail(
            query = item.title,
            isSeries = isSeries,
            tmdbId = item.tmdbId
        )
    }

    val backdropList = remember(tmdbDetail, item.posterUrl) {
        val fromTmdb = tmdbDetail?.backdropUrls?.filter { it.isNotBlank() }.orEmpty()
        when {
            fromTmdb.isNotEmpty() -> fromTmdb
            !tmdbDetail?.backdropUrl.isNullOrBlank() -> listOf(tmdbDetail!!.backdropUrl!!)
            !tmdbDetail?.posterUrl.isNullOrBlank() -> listOf(tmdbDetail!!.posterUrl!!)
            item.posterUrl.isNotBlank() -> listOf(item.posterUrl)
            else -> emptyList()
        }
    }
    var currentBackdropIndex by remember(backdropList) { mutableIntStateOf(0) }
    LaunchedEffect(backdropList) {
        currentBackdropIndex = 0
        if (backdropList.size > 1) {
            while (true) {
                kotlinx.coroutines.delay(3500L)
                currentBackdropIndex = (currentBackdropIndex + 1) % backdropList.size
            }
        }
    }
    val headerImageUrl = backdropList.getOrNull(currentBackdropIndex) ?: tmdbDetail?.backdropUrl ?: tmdbDetail?.posterUrl ?: item.posterUrl
    val runtimeMinutes = tmdbDetail?.runtimeMinutes
    val directors = tmdbDetail?.directors ?: emptyList()
    val castList = tmdbDetail?.cast ?: emptyList()
    val displayOverview = tmdbDetail?.overview?.takeIf { it.isNotBlank() } ?: item.overview

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.example.util.KeepSystemBarsHidden()
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = if (isLandscape) 780.dp else 680.dp)
                .padding(vertical = if (isLandscape) 6.dp else 16.dp, horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A28)),
            border = BorderStroke(1.dp, Color(0xFF2E2E42))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 120.dp else 190.dp)
                ) {
                    androidx.compose.animation.Crossfade(
                        targetState = headerImageUrl,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 700),
                        modifier = Modifier.fillMaxSize(),
                        label = "imdb_detail_backdrop_slide"
                    ) { bgUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(bgUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF1A1A28)),
                                    startY = if (isLandscape) 40f else 100f
                                )
                            )
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(if (isLandscape) 30.dp else 36.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_desc),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFFF5C518),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(if (isLandscape) 8.dp else 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = if (isLandscape) 2.dp else 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${item.imdbRating} IMDb",
                                color = Color.Black,
                                fontSize = if (isLandscape) 12.sp else 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = if (isLandscape) 8.dp else 16.dp)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = if (isLandscape) 16.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(if (isLandscape) 3.dp else 6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = if (!isSeries) Color(0xFF1E88E5) else Color(0xFF8E24AA),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = item.type,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "📅 ${item.releaseDate}",
                            color = Color(0xFFFFB300),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (runtimeMinutes != null && runtimeMinutes > 0) {
                            Text(
                                text = if (isSeries) {
                                    stringResource(R.string.info_runtime_series, runtimeMinutes)
                                } else {
                                    stringResource(R.string.info_runtime_movie, runtimeMinutes)
                                },
                                color = Color(0xFFE0E0E0),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 10.dp))

                    Text(
                        text = stringResource(R.string.imdb_genres_label, item.genres.joinToString(", ")),
                        color = Color.LightGray,
                        fontSize = 11.5.sp
                    )

                    if (directors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(if (isLandscape) 2.dp else 6.dp))
                        val joinedDirectors = directors.joinToString(", ")
                        Text(
                            text = if (isSeries) {
                                stringResource(R.string.info_creator, joinedDirectors)
                            } else {
                                stringResource(R.string.info_director, joinedDirectors)
                            },
                            color = Color(0xFFCFD8DC),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (castList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val summaryNames = castList.take(5).joinToString(", ") { it.name }
                        Text(
                            text = stringResource(R.string.info_cast_summary, summaryNames),
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.5.sp,
                            maxLines = if (isLandscape) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 10.dp))

                    if (!isLandscape) {
                        Text(
                            text = stringResource(R.string.imdb_overview_label),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = displayOverview,
                        color = Color.Gray,
                        fontSize = if (isLandscape) 12.sp else 13.sp,
                        lineHeight = if (isLandscape) 16.sp else 18.sp,
                        maxLines = if (isOverviewExpanded) 15 else if (isLandscape) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { isOverviewExpanded = !isOverviewExpanded }
                    )

                    if (castList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 12.dp))
                        Text(
                            text = stringResource(R.string.info_cast_section_title),
                            color = Color.White,
                            fontSize = if (isLandscape) 12.5.sp else 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp),
                            contentPadding = PaddingValues(bottom = 2.dp)
                        ) {
                            items(castList) { member ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(if (isLandscape) 64.dp else 72.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isLandscape) 44.dp else 58.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF232A38)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!member.photoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(member.photoUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = member.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = member.name,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(if (isLandscape) 22.dp else 28.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = member.name,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (member.character.isNotBlank()) {
                                        Text(
                                            text = member.character,
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 12.dp))

                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.LightGray
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3E3E52)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.close_desc),
                                    color = Color.LightGray,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = onNotifyToggle,
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isNotified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isNotified) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isNotified) stringResource(R.string.imdb_notify_active) else stringResource(R.string.imdb_notify_btn),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.LightGray
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3E3E52)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.close_desc),
                                color = Color.LightGray,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = onNotifyToggle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isNotified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isNotified) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isNotified) stringResource(R.string.imdb_notify_active) else stringResource(R.string.imdb_notify_btn),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
