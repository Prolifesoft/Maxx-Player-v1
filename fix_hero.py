with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

import re

# Find the HeroBanner function completely
start_idx = content.find("@Composable\nfun HeroBanner(item: M3uItem, onClick: () -> Unit) {")
if start_idx != -1:
    # Find the end of HeroBanner
    # We will search for the next @Composable or the end of the file
    next_composable = content.find("@Composable\nfun MovieCard", start_idx)
    if next_composable != -1:
        old_hero = content[start_idx:next_composable]
        
        new_hero = """@Composable
fun HeroBanner(item: M3uItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(onClick = onClick)
            .background(Color(0xFF0F0F17))
    ) {
        // Background Image (dimmed and cropped)
        if (!item.logo.isNullOrEmpty()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(item.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.2f),
                contentScale = ContentScale.Crop
            )
        }

        // Gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
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
        var showReportDialog by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current

        // Top Right Action Buttons
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            IconButton(
                onClick = { com.example.model.FavoritesManager.toggleFavorite(item.url) },
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    if (isFavorite) androidx.compose.material.icons.Icons.Default.Favorite else androidx.compose.material.icons.Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color.Red else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
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
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    if (isLocked) Icons.Default.Lock else androidx.compose.material.icons.Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isLocked) Color(0xFFEF5350) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { showReportDialog = true },
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(20.dp))
            }
        }

        // Foreground Content (Poster and Text)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text & Button on the left
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.play), fontWeight = FontWeight.Bold)
                }
            }

            // Un-cropped Poster on the right
            if (!item.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .height(180.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(180.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VideoLibrary, 
                        contentDescription = null, 
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                }
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
                message = "'${item.title}' akışında sorun bildirildi.",
                onDismiss = { showReportDialog = false }
            )
        }
    }
}

"""
        content = content.replace(old_hero, new_hero)
        print("HeroBanner replaced successfully!")
    else:
        print("Could not find next composable")
else:
    print("Could not find HeroBanner start")

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)

