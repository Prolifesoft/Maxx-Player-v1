with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

import_blur = "import androidx.compose.ui.draw.blur"
import_alpha = "import androidx.compose.ui.draw.alpha"
if import_blur not in content:
    content = content.replace("import androidx.compose.ui.draw.clip", import_blur + "\n" + import_alpha + "\nimport androidx.compose.ui.draw.clip")

old_hero_start = """fun HeroBanner(item: M3uItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
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
        }"""

new_hero_start = """fun HeroBanner(item: M3uItem, onClick: () -> Unit) {
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
        }"""

if old_hero_start in content:
    content = content.replace(old_hero_start, new_hero_start)
    print("Replaced HeroBanner Image")
else:
    print("Could not find HeroBanner Image")

old_gradient = """        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        startY = 100f
                    )
                )
        )"""

new_gradient = """        Box(
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

if old_gradient in content:
    content = content.replace(old_gradient, new_gradient)
    print("Replaced Gradient")
else:
    print("Could not find Gradient")


with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
