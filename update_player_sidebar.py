import re

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

# Add imports for Icons if needed
imports_to_add = """import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
"""

if "import androidx.compose.material.icons.filled.Close" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Report", imports_to_add + "import androidx.compose.material.icons.filled.Report")

old_sheet_block = """        if (showPlaylistSheet) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showPlaylistSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Kategori Listesi", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
                    androidx.compose.foundation.lazy.LazyColumn {
                        val playlist = PlayerRepository.currentPlaylist
                        items(playlist.size) { index ->
                            val item = playlist[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playingItem = item
                                        PlayerRepository.currentlyPlayingItem = item
                                        exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(item.url)))
                                        exoPlayer.prepare()
                                        exoPlayer.playWhenReady = true
                                        showPlaylistSheet = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.title ?: "Bilinmeyen",
                                    color = if (item.url == playingItem?.url) MaterialTheme.colorScheme.primary else Color.White,
                                    fontWeight = if (item.url == playingItem?.url) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }"""

new_sidebar_block = """        AnimatedVisibility(
            visible = showPlaylistSheet,
            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showPlaylistSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .background(Color(0xFF12121A).copy(alpha = 0.95f))
                        .clickable(enabled = false) {}
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Kanal Listesi",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            val playlist = PlayerRepository.currentPlaylist
                            Text(
                                "${playlist.size} Yayın",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        androidx.compose.material3.IconButton(
                            onClick = { showPlaylistSheet = false }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color.White
                            )
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var searchQuery by remember { mutableStateOf("") }
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Kanal Ara...", color = Color.Gray, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                androidx.compose.material3.IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedContainerColor = Color(0xFF1E1E28),
                            unfocusedContainerColor = Color(0xFF1E1E28),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    val playlist = PlayerRepository.currentPlaylist
                    val filteredList = remember(playlist, searchQuery) {
                        if (searchQuery.isBlank()) playlist
                        else playlist.filter { (it.title ?: "").contains(searchQuery, ignoreCase = true) }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredList.size) { index ->
                            val item = filteredList[index]
                            val isSelected = item.url == playingItem?.url

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        playingItem = item
                                        PlayerRepository.currentlyPlayingItem = item
                                        exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(item.url)))
                                        exoPlayer.prepare()
                                        exoPlayer.playWhenReady = true
                                        showPlaylistSheet = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                                    )
                                }
                                Text(
                                    text = item.title ?: "Bilinmeyen",
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }"""

if old_sheet_block in content:
    content = content.replace(old_sheet_block, new_sidebar_block)
    with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
        f.write(content)
    print("Successfully replaced player list view with TV left sidebar!")
else:
    print("Could not find old_sheet_block exactly.")
