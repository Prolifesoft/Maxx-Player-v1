import re

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

# 1. Update `val tabs = ...`
old_tabs = """    val tabs = listOf(stringResource(R.string.tab_home), stringResource(R.string.tab_movies), stringResource(R.string.tab_series), stringResource(R.string.tab_live), stringResource(R.string.tab_favorites))"""
new_tabs = """    val tabs = listOf(stringResource(R.string.tab_home), stringResource(R.string.tab_live), stringResource(R.string.tab_movies), stringResource(R.string.tab_series), stringResource(R.string.tab_favorites))"""
content = content.replace(old_tabs, new_tabs)

# 2. Update `val tabIcons = ...`
old_icons = """val tabIcons = listOf(Icons.Default.Home, Icons.Default.Movie, Icons.Default.Tv, Icons.Default.LiveTv, Icons.Default.Favorite)"""
new_icons = """val tabIcons = listOf(Icons.Default.Home, Icons.Default.LiveTv, Icons.Default.Movie, Icons.Default.Tv, Icons.Default.Favorite)"""
content = content.replace(old_icons, new_icons)

# 3. Update `val currentType = ...`
old_current_type = """    val currentType = remember(selectedTabIndex) {
        when(selectedTabIndex) {
            1 -> com.example.parser.ItemType.MOVIE
            2 -> com.example.parser.ItemType.SERIES
            3 -> com.example.parser.ItemType.LIVE
            else -> com.example.parser.ItemType.LIVE
        }
    }"""
new_current_type = """    val currentType = remember(selectedTabIndex) {
        when(selectedTabIndex) {
            1 -> com.example.parser.ItemType.LIVE
            2 -> com.example.parser.ItemType.MOVIE
            3 -> com.example.parser.ItemType.SERIES
            else -> com.example.parser.ItemType.LIVE
        }
    }"""
content = content.replace(old_current_type, new_current_type)

# 4. Update the if/else if branches inside `Box(modifier = Modifier.weight(1f).fillMaxWidth())`
# The current is:
# } else if (selectedTabIndex == 3) {
# => Live UI
# } else if (selectedTabIndex == 2) {
# => Series UI
# } else {
# => Movies UI

old_live_if = "} else if (selectedTabIndex == 3) {"
new_live_if = "} else if (selectedTabIndex == 1) {"
content = content.replace(old_live_if, new_live_if)

old_series_if = "} else if (selectedTabIndex == 2) { // Series"
new_series_if = "} else if (selectedTabIndex == 3) { // Series"
content = content.replace(old_series_if, new_series_if)

# The "else" for Movies doesn't need to change, as it will now implicitly catch index 2 instead of 1.

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
