with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

import re

# We want to change the GridCells for Live TV (selectedTabIndex == 1)
# Currently it's GridCells.Fixed(...)
# Let's find it.
old_live_grid = """                } else if (selectedTabIndex == 1) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 6 else 4),
                        contentPadding = PaddingValues(16.dp),"""
                        
new_live_grid = """                } else if (selectedTabIndex == 1) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 5 else 3),
                        contentPadding = PaddingValues(16.dp),"""

if old_live_grid in content:
    content = content.replace(old_live_grid, new_live_grid)
    print("Replaced Live TV grid")
else:
    print("Could not find Live TV grid")

# We should also reduce padding and adjust aspect ratio in ChannelCard to make logos bigger.
old_channel_card = """    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clickable(onClick = onClick),"""
            
new_channel_card = """    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f) // Square looks better for logos
            .clickable(onClick = onClick),"""

content = content.replace(old_channel_card, new_channel_card)

old_channel_img = """                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)),"""

new_channel_img = """                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(4.dp) // Less padding = bigger logo
                            .clip(RoundedCornerShape(8.dp)),"""
                            
content = content.replace(old_channel_img, new_channel_img)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
