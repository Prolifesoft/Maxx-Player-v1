import re

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

# Add Icon import
if "import androidx.compose.material.icons.filled.List" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Report", "import androidx.compose.material.icons.filled.Report\nimport androidx.compose.material.icons.filled.List")

# Add state for Playlist Sheet
content = content.replace("var showReportDialog by remember { mutableStateOf(false) }", "var showReportDialog by remember { mutableStateOf(false) }\n    var showPlaylistSheet by remember { mutableStateOf(false) }")

# Update FloatingActionButton container to include both buttons
old_fab = """            FloatingActionButton(
                onClick = { showReportDialog = true },
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(20.dp))
            }"""

new_fab = """            AnimatedVisibility(
                visible = isControllerVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(
                        onClick = { showPlaylistSheet = true },
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = "Kategori Listesi", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    FloatingActionButton(
                        onClick = { showReportDialog = true },
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(20.dp))
                    }
                }
            }"""
content = content.replace(old_fab, new_fab)

# Add Playlist Sheet UI
playlist_sheet_ui = """
        if (showPlaylistSheet) {
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
        }
"""
content = content.replace("        if (showReportDialog) {", playlist_sheet_ui + "\n        if (showReportDialog) {")

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)
