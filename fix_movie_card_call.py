with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

# Fix default modifier for MovieCard, SeriesCard, ProgressCard
content = content.replace("fun MovieCard(item: M3uItem, onClick: () -> Unit, modifier: Modifier = Modifier) {", "fun MovieCard(item: M3uItem, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {")
content = content.replace("fun SeriesCard(seriesName: String, item: M3uItem, episodeCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {", "fun SeriesCard(seriesName: String, item: M3uItem, episodeCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {")
content = content.replace("fun ProgressCard(progress: com.example.model.db.PlaybackProgressEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {", "fun ProgressCard(progress: com.example.model.db.PlaybackProgressEntity, onClick: () -> Unit, modifier: Modifier = Modifier.width(160.dp)) {")

# In DashboardScreen, tab 2 (Movies)
# Let's find the place where we have `val uncategorizedText = ...`
# and insert width calculation.
start_idx = content.find("val uncategorizedText = stringResource(R.string.uncategorized)")
if start_idx != -1:
    old_movie_lazy_col = content[start_idx : content.find("val itemsByGroup =", start_idx)]
    new_movie_lazy_col = """val uncategorizedText = stringResource(R.string.uncategorized)
                val config = LocalConfiguration.current
                val isPort = config.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                val cardWidth = (config.screenWidthDp.dp - 48.dp) / if (isPort) 4 else 6
"""
    content = content.replace(old_movie_lazy_col, new_movie_lazy_col)

# Replace MovieCard(item = item, onClick = { 
content = content.replace("""                                    items(groupItems) { item ->
                                        MovieCard(item = item, onClick = {""", """                                    items(groupItems) { item ->
                                        MovieCard(item = item, modifier = Modifier.width(cardWidth), onClick = {""")
                                        
# Replace ProgressCard(progress = progress, onClick = {
content = content.replace("""                                items(recentMovies) { progress ->
                                    ProgressCard(progress = progress, onClick = {""", """                                items(recentMovies) { progress ->
                                    ProgressCard(progress = progress, modifier = Modifier.width(cardWidth * 1.5f), onClick = {""")

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
