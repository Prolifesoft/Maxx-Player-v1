with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

imports = """import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
"""

if "import androidx.compose.material3.MaterialTheme" not in content:
    content = content.replace("import androidx.compose.material3.Text", imports + "import androidx.compose.material3.Text")
    
if "@OptIn(UnstableApi::class)" in content:
    content = content.replace("@OptIn(UnstableApi::class)", "@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)")

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)
