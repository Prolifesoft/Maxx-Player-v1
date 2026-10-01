with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

# Update MovieCard modifier
old_movie_card = """    Card(
        modifier = Modifier
            .width(100.dp)
            .height(140.dp)
            .clickable(onClick = onClick),"""

new_movie_card = """    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f)
            .clickable(onClick = onClick),"""

if old_movie_card in content:
    content = content.replace(old_movie_card, new_movie_card)
    print("Updated MovieCard modifier.")

# Update SeriesCard aspect ratio
old_series_card = """    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onClick),"""

new_series_card = """    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f)
            .clickable(onClick = onClick),"""

if old_series_card in content:
    content = content.replace(old_series_card, new_series_card)
    print("Updated SeriesCard modifier.")

# Update ImdbCard height to aspectRatio
old_imdb_box = """            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {"""

new_imdb_box = """            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
            ) {"""

if old_imdb_box in content:
    content = content.replace(old_imdb_box, new_imdb_box)
    print("Updated ImdbCard poster box.")

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)

