import sys
import re

content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

def get_block(content, start_marker):
    idx = content.find(start_marker)
    if idx == -1: return None, -1, -1
    
    braces = 0
    in_block = False
    
    for i in range(idx, len(content)):
        if content[i] == '{':
            braces += 1
            in_block = True
        elif content[i] == '}':
            braces -= 1
        
        if in_block and braces == 0:
            return content[idx:i+1], idx, i+1
            
    return None, -1, -1

old_series, start, end = get_block(content, "fun SeriesCard(seriesName: String, item: M3uItem, episodeCount: Int, onClick: () -> Unit) {")

new_series = """fun SeriesCard(seriesName: String, item: M3uItem, episodeCount: Int, onClick: () -> Unit) {
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
            
            val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
            val isFavorite = favoriteUrls.contains(item.url)
            val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
            val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
            val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
            val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
            var showUnlockDialog by remember { mutableStateOf(false) }
            val context = androidx.compose.ui.platform.LocalContext.current
            
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
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
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = seriesName,
                        fontSize = 10.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "$episodeCount " + stringResource(R.string.episodes_suffix),
                        fontSize = 8.sp,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}"""

if old_series:
    content = content[:start] + new_series + content[end:]
    open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w").write(content)
    print("SeriesCard rewritten successfully.")
else:
    print("SeriesCard not found!")
