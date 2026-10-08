package com.example.ui.screens.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import coil.compose.AsyncImage
import com.example.model.CategoryManager
import com.example.model.ParentalControlManager
import com.example.model.PlayerRepository
import com.example.model.PlaylistRepository
import com.example.ui.theme.RedPrimary
import com.example.R
import com.example.parser.ItemType
import com.example.parser.M3uItem
import com.example.ui.components.PinUnlockDialog
import com.example.util.isTv

@Composable
fun PlayerSidePanel(
    isOpen: Boolean,
    playingItem: M3uItem?,
    epPrefix: String,
    onClose: () -> Unit,
    onPlayItem: (M3uItem, List<M3uItem>) -> Unit,
    onRegisterKeyHandler: (((AndroidKeyEvent) -> Boolean)?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen) {
        LaunchedEffect(Unit) {
            onRegisterKeyHandler(null)
        }
        return
    }

    val context = LocalContext.current
    val localView = LocalView.current
    val isTvDevice = remember(context) { isTv(context) }
    val panelFocusRequester = remember { FocusRequester() }
    val panelOpenTimeMs = remember(isOpen) { System.currentTimeMillis() }
    val panelOpenUptimeMs = remember(isOpen) { android.os.SystemClock.uptimeMillis() }

    // Back tuşu paneli kapatsın
    BackHandler(enabled = isOpen) {
        onClose()
    }

    val isFavoritesMode = PlayerRepository.isFavoritesPlaylist && PlayerRepository.currentPlaylist.isNotEmpty()
    val isLive = playingItem?.type == ItemType.LIVE && !isFavoritesMode

    // Canlı için veri kaynağı: PlaylistRepository.playlist içindeki canlı yayınlar
    val allItems by PlaylistRepository.playlist.collectAsState()
    val hiddenCategories by CategoryManager.hiddenCategories.collectAsState()

    val liveItems = remember(allItems, hiddenCategories) {
        allItems.filter { it.type == ItemType.LIVE && !hiddenCategories.contains(it.group ?: "") }
    }

    val liveCategories = remember(liveItems) {
        val groups = liveItems.map { it.group?.ifBlank { "Diğer" } ?: "Diğer" }.distinct()
        if (groups.isEmpty()) listOf("Genel") else groups
    }

    // Başlangıç kategorisi = oynayan kanalın group'u
    var currentCategoryIndex by remember(isOpen) {
        val initialGroup = playingItem?.group?.ifBlank { "Diğer" } ?: "Diğer"
        val idx = liveCategories.indexOf(initialGroup)
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }

    val currentCategory = liveCategories.getOrElse(currentCategoryIndex) { "Genel" }

    val channelsInCat = remember(currentCategory, liveItems) {
        liveItems.filter { (it.group?.ifBlank { "Diğer" } ?: "Diğer") == currentCategory }
    }
    val playlist = PlayerRepository.currentPlaylist
    val activeItems = if (isLive) channelsInCat else playlist

    // Seçili / odaklanmış öğe indeksi (Kategori değiştiğinde veya panel açıldığında güncellenir)
    var focusedIndex by remember(currentCategory, isLive, isOpen) {
        val list = if (isLive) {
            liveItems.filter { (it.group?.ifBlank { "Diğer" } ?: "Diğer") == currentCategory }
        } else {
            PlayerRepository.currentPlaylist
        }
        val activeIdx = list.indexOfFirst { it.url == playingItem?.url }
        mutableIntStateOf(if (activeIdx >= 0) activeIdx else 0)
    }

    LaunchedEffect(activeItems.size) {
        if (activeItems.isNotEmpty() && focusedIndex >= activeItems.size) {
            focusedIndex = 0
        }
    }

    val listState = rememberLazyListState()

    // Kategori değiştiğinde veya panel açıldığında doğrudan ilgili öğeye konumlan
    LaunchedEffect(currentCategory, isLive, isOpen) {
        if (activeItems.isNotEmpty()) {
            val targetIdx = focusedIndex.coerceIn(0, activeItems.lastIndex)
            listState.scrollToItem(targetIdx)
        }
    }

    // Kumandadan yukarı/aşağı ile focusedIndex değiştiğinde öğeyi görünür alanda tut
    LaunchedEffect(focusedIndex) {
        if (activeItems.isNotEmpty() && focusedIndex in activeItems.indices) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                listState.scrollToItem(focusedIndex)
            } else {
                val itemInfo = visibleItems.find { it.index == focusedIndex }
                val viewportStart = layoutInfo.viewportStartOffset
                val viewportEnd = layoutInfo.viewportEndOffset
                if (itemInfo == null) {
                    val lastVisible = visibleItems.last().index
                    if (focusedIndex > lastVisible) {
                        val visibleCount = (visibleItems.size - 1).coerceAtLeast(1)
                        val newFirst = (focusedIndex - visibleCount + 1).coerceAtLeast(0)
                        listState.scrollToItem(newFirst)
                        val updatedInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == focusedIndex }
                        val updatedEnd = listState.layoutInfo.viewportEndOffset
                        if (updatedInfo != null && updatedInfo.offset + updatedInfo.size > updatedEnd) {
                            listState.scrollBy(((updatedInfo.offset + updatedInfo.size) - updatedEnd).toFloat())
                        } else if (updatedInfo == null) {
                            listState.scrollToItem(focusedIndex)
                        }
                    } else {
                        listState.scrollToItem(focusedIndex)
                    }
                } else if (itemInfo.offset < viewportStart) {
                    listState.scrollToItem(focusedIndex)
                } else if (itemInfo.offset + itemInfo.size > viewportEnd) {
                    val diff = (itemInfo.offset + itemInfo.size) - viewportEnd
                    listState.scrollBy(diff.toFloat())
                }
            }
        }
    }

    // PIN Unlock Dialog states
    var showPinDialogForCategory by remember { mutableStateOf(false) }
    var pendingCategoryIndex by remember { mutableIntStateOf(0) }

    var showPinDialogForItem by remember { mutableStateOf(false) }
    var pendingPlayItem by remember { mutableStateOf<M3uItem?>(null) }
    var pendingPlayList by remember { mutableStateOf<List<M3uItem>>(emptyList()) }

    fun navigateCategory(delta: Int) {
        if (liveCategories.isEmpty()) return
        val newIndex = (currentCategoryIndex + delta + liveCategories.size) % liveCategories.size
        val nextCat = liveCategories[newIndex]

        if (ParentalControlManager.isGroupLocked(nextCat)) {
            pendingCategoryIndex = newIndex
            showPinDialogForCategory = true
        } else {
            currentCategoryIndex = newIndex
        }
    }

    fun handleItemClick(item: M3uItem, activeList: List<M3uItem>) {
        val group = item.group ?: ""
        val isLocked = ParentalControlManager.isGroupLocked(group) || ParentalControlManager.isItemLocked(item)
        if (isLocked) {
            pendingPlayItem = item
            pendingPlayList = activeList
            showPinDialogForItem = true
        } else {
            if (item.url != playingItem?.url) {
                onPlayItem(item, activeList)
            } else {
                PlayerRepository.currentPlaylist = activeList
            }
            onClose()
        }
    }

    var lastHandledEventTime by remember { mutableLongStateOf(-1L) }
    var lastHandledKeyCode by remember { mutableIntStateOf(-1) }

    val handleNativeKey: (AndroidKeyEvent) -> Boolean = { nativeEvent ->
        if (showPinDialogForCategory || showPinDialogForItem) {
            false
        } else if (nativeEvent.action == AndroidKeyEvent.ACTION_UP) {
            when (nativeEvent.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> isLive
                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> true
                else -> false
            }
        } else if (nativeEvent.action != AndroidKeyEvent.ACTION_DOWN) {
            false
        } else if (nativeEvent.eventTime == lastHandledEventTime && nativeEvent.keyCode == lastHandledKeyCode) {
            true
        } else {
            val handled = when (nativeEvent.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isLive) {
                        navigateCategory(-1)
                        true
                    } else false
                }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isLive) {
                        navigateCategory(1)
                        true
                    } else false
                }
                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (activeItems.isNotEmpty()) {
                        focusedIndex = (focusedIndex + 1) % activeItems.size
                    }
                    true
                }
                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (activeItems.isNotEmpty()) {
                        focusedIndex = (focusedIndex - 1 + activeItems.size) % activeItems.size
                    }
                    true
                }
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    val nowMs = System.currentTimeMillis()
                    val isOpeningPressOrRepeat = nativeEvent.repeatCount > 0 ||
                        nativeEvent.downTime <= panelOpenUptimeMs + 100L ||
                        (nowMs - panelOpenTimeMs) < 400L
                    if (!isOpeningPressOrRepeat && activeItems.isNotEmpty() && focusedIndex in activeItems.indices) {
                        handleItemClick(activeItems[focusedIndex], activeItems)
                    }
                    true
                }
                else -> false
            }
            if (handled) {
                lastHandledEventTime = nativeEvent.eventTime
                lastHandledKeyCode = nativeEvent.keyCode
            }
            handled
        }
    }

    val currentKeyHandler by rememberUpdatedState(handleNativeKey)

    // Panel açıldığında odağı panele ver ve üst bileşene key handler kaydet
    LaunchedEffect(isOpen) {
        onRegisterKeyHandler { ev -> currentKeyHandler(ev) }
        try {
            panelFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Touch mode / hibrit kumanda durumlarında bile yön tuşlarını yakalamak için View seviyesi dinleyici
    DisposableEffect(localView, isOpen) {
        onRegisterKeyHandler { ev -> currentKeyHandler(ev) }
        val unhandledListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            if (event != null) currentKeyHandler(event) else false
        }
        ViewCompat.addOnUnhandledKeyEventListener(localView, unhandledListener)
        onDispose {
            ViewCompat.removeOnUnhandledKeyEventListener(localView, unhandledListener)
            onRegisterKeyHandler(null)
        }
    }

    val panelWidth = if (isTvDevice) 420.dp else 340.dp

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 15.dp, bottom = 15.dp)
                .width(panelWidth)
                .background(
                    Color(0xFF0D0D15).copy(alpha = 0.96f),
                    RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                )
                .padding(vertical = 12.dp, horizontal = 12.dp)
                // Kumanda YUKARI/AŞAĞI (liste) ve SOL/SAĞ (kategori) değişimi (.focusable()'dan ÖNCE olmalı!)
                .onPreviewKeyEvent { keyEvent ->
                    currentKeyHandler(keyEvent.nativeKeyEvent)
                }
                .focusRequester(panelFocusRequester)
                .focusable()
                // Panelde yatay kaydırma ile kategori değişimi
                .pointerInput(isLive, liveCategories.size, currentCategoryIndex) {
                    if (isLive && liveCategories.size > 1) {
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onDragEnd = {
                                if (totalDragX > 70f) {
                                    navigateCategory(-1)
                                } else if (totalDragX < -70f) {
                                    navigateCategory(1)
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount
                            }
                        )
                    }
                }
        ) {
            if (isLive) {
                // Canlı yayın: Üst satır: [‹] "Kategori adı" (sıra/toplam) [›]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navigateCategory(-1) },
                        modifier = Modifier
                            .size(36.dp)
                            .focusProperties { canFocus = false }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Önceki Kategori",
                            tint = Color.White
                        )
                    }

                    val catText = "$currentCategory (${currentCategoryIndex + 1}/${liveCategories.size})"
                    Text(
                        text = catText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = { navigateCategory(1) },
                        modifier = Modifier
                            .size(36.dp)
                            .focusProperties { canFocus = false }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Sonraki Kategori",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .focusProperties { canFocus = false }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_desc),
                            tint = Color.Gray
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Kategoriye ait kanallar listesi
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = channelsInCat,
                        key = { idx, item -> "${currentCategory}_${idx}_${item.url}" }
                    ) { idx, item ->
                        val isSelected = item.url == playingItem?.url
                        val isItemFocused = idx == focusedIndex
                        val isLocked = ParentalControlManager.isGroupLocked(item.group ?: "") ||
                                ParentalControlManager.isItemLocked(item)

                        TvChannelRowItem(
                            item = item,
                            displayName = item.title,
                            isSelected = isSelected,
                            isItemFocused = isItemFocused,
                            isLocked = isLocked,
                            onClick = {
                                focusedIndex = idx
                                handleItemClick(item, channelsInCat)
                            }
                        )
                    }
                }
            } else {
                // Film/Dizi/Favori: Üst satırda başlık (favoride "Favoriler", dizide "Bölümler", filmde "Filmler")
                val isSeries = playingItem?.type == ItemType.SERIES
                val panelTitle = when {
                    isFavoritesMode -> stringResource(R.string.favourites)
                    isSeries -> stringResource(R.string.player_episodes)
                    else -> stringResource(R.string.player_movies)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$panelTitle (${playlist.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .focusProperties { canFocus = false }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_desc),
                            tint = Color.Gray
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = playlist,
                        key = { idx, item -> "pl_${idx}_${item.url}" }
                    ) { idx, item ->
                        val isSelected = item.url == playingItem?.url
                        val isItemFocused = idx == focusedIndex
                        val isLocked = ParentalControlManager.isGroupLocked(item.group ?: "") ||
                                ParentalControlManager.isItemLocked(item)

                        val title = if (item.type == ItemType.SERIES) {
                            val ep = item.episode?.let { "$epPrefix$it: " } ?: ""
                            "$ep${item.title}"
                        } else {
                            item.title
                        }

                        TvChannelRowItem(
                            item = item,
                            displayName = title,
                            isSelected = isSelected,
                            isItemFocused = isItemFocused,
                            isLocked = isLocked,
                            onClick = {
                                focusedIndex = idx
                                handleItemClick(item, playlist)
                            }
                        )
                    }
                }
            }
        }

        // Sağdaki karartılmış boş alana dokunulduğunda (yalnızca dokunmatik/fare) paneli kapat
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusProperties { canFocus = false }
                .pointerInput(Unit) {
                    detectTapGestures { onClose() }
                }
        )
    }

    // Kategori Kilit Doğrulama
    if (showPinDialogForCategory) {
        PinUnlockDialog(
            onUnlock = {
                currentCategoryIndex = pendingCategoryIndex
                showPinDialogForCategory = false
            },
            onDismiss = {
                showPinDialogForCategory = false
            }
        )
    }

    // Kanal/Öğe Kilit Doğrulama
    if (showPinDialogForItem) {
        PinUnlockDialog(
            onUnlock = {
                pendingPlayItem?.let {
                    onPlayItem(it, pendingPlayList)
                    onClose()
                }
                pendingPlayItem = null
                pendingPlayList = emptyList()
                showPinDialogForItem = false
            },
            onDismiss = {
                pendingPlayItem = null
                pendingPlayList = emptyList()
                showPinDialogForItem = false
            }
        )
    }
}

@Composable
private fun TvChannelRowItem(
    item: M3uItem,
    displayName: String,
    isSelected: Boolean,
    isItemFocused: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = isItemFocused

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> RedPrimary.copy(alpha = 0.38f)
                    isSelected -> RedPrimary.copy(alpha = 0.20f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) RedPrimary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusProperties { canFocus = false }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sol logo (40dp)
        var logoLoadFailed by remember(item.logo) { mutableStateOf(false) }
        if (!item.logo.isNullOrBlank() && !logoLoadFailed) {
            AsyncImage(
                model = item.logo,
                contentDescription = item.title,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF080F19)),
                contentScale = ContentScale.Fit,
                placeholder = androidx.compose.ui.res.painterResource(id = R.drawable.img_app_icon),
                error = androidx.compose.ui.res.painterResource(id = R.drawable.img_app_icon),
                onError = { logoLoadFailed = true }
            )
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.img_app_icon),
                contentDescription = item.title,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF080F19)),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = displayName,
            color = when {
                isFocused -> Color.White
                isSelected -> RedPrimary
                else -> Color.White
            },
            fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.Normal,
            fontSize = if (isFocused) 15.sp else 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isLocked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Kilitli",
                tint = Color(0xFFE53935),
                modifier = Modifier
                    .size(16.dp)
                    .padding(start = 4.dp)
            )
        } else if (isSelected || isFocused) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = if (isSelected) "Oynatılıyor" else "Seçili",
                tint = RedPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
