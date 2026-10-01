with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

# Replace Adaptive grids
old_adaptive = "GridCells.Adaptive(minSize = 120.dp)"
new_fixed = "GridCells.Fixed(if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 6 else 4)"
content = content.replace(old_adaptive, new_fixed)

# Now for the LazyRow items (Movies and recent items), we should set a specific width.
# Instead of modifying the components themselves which use `fillMaxWidth()`, 
# we can just pass a modifier to them or wrap them in a Box with specific width.
# Wait, MovieCard doesn't accept a modifier. Let's modify MovieCard definition.

# Find MovieCard definition
if "fun MovieCard(item: M3uItem, onClick: () -> Unit)" in content:
    content = content.replace("fun MovieCard(item: M3uItem, onClick: () -> Unit) {", "fun MovieCard(item: M3uItem, onClick: () -> Unit, modifier: Modifier = Modifier) {")
    content = content.replace("""    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f)
            .clickable(onClick = onClick),""", """    Card(
        modifier = modifier
            .aspectRatio(0.67f)
            .clickable(onClick = onClick),""")

# Find SeriesCard definition
if "fun SeriesCard(seriesName: String, item: M3uItem, episodeCount: Int, onClick: () -> Unit)" in content:
    content = content.replace("fun SeriesCard(seriesName: String, item: M3uItem, episodeCount: Int, onClick: () -> Unit) {", "fun SeriesCard(seriesName: String, item: M3uItem, episodeCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {")
    content = content.replace("""    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f)
            .clickable(onClick = onClick),""", """    Card(
        modifier = modifier
            .aspectRatio(0.67f)
            .clickable(onClick = onClick),""")
            
# Find ProgressCard definition
if "fun ProgressCard(progress: com.example.model.db.PlaybackProgressEntity, onClick: () -> Unit)" in content:
    content = content.replace("fun ProgressCard(progress: com.example.model.db.PlaybackProgressEntity, onClick: () -> Unit) {", "fun ProgressCard(progress: com.example.model.db.PlaybackProgressEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {")
    content = content.replace("""    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),""", """    Card(
        modifier = modifier
            .clickable(onClick = onClick),""")


with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
