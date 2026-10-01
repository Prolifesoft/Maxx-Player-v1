with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

old_hero_full = """@Composable
fun HeroBanner(item: M3uItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clickable(onClick = onClick)
    ) {
        if (!item.logo.isNullOrEmpty()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(item.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(24.dp).alpha(0.5f),
                contentScale = ContentScale.Crop,
                error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
            )
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(item.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize().padding(end = 16.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterEnd
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
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(MaterialTheme.colorScheme.background, Color.Transparent),
                        startX = 0f,
                        endX = 800f
                    )
                )
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        startY = 150f
                    )
                )
        )"""

new_hero_full = """@Composable
fun HeroBanner(item: M3uItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(onClick = onClick)
            .background(Color(0xFF14141E))
    ) {
        // Arkaplan bulanık resim
        if (!item.logo.isNullOrEmpty()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(item.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(40.dp).alpha(0.4f),
                contentScale = ContentScale.Crop,
                error = coil.compose.rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
            )
        }
        
        // Karartma katmanı (Yazıların okunması için)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background.copy(alpha = 0.8f), Color.Transparent),
                        startX = 0f,
                        endX = 900f
                    )
                )
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.5f), MaterialTheme.colorScheme.background),
                        startY = 200f
                    )
                )
        )

        // Asıl Afiş (Sağ tarafta, düzgün boyutlandırılmış)
        if (!item.logo.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 24.dp, top = 24.dp, bottom = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.67f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 24.dp, top = 24.dp, bottom = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.67f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface),
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
        }"""

if old_hero_full in content:
    content = content.replace(old_hero_full, new_hero_full)
    print("Replaced HeroBanner FULL")
else:
    print("Could not find HeroBanner FULL")

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
