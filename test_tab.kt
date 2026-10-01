import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*

@Composable
fun MyTab() {
    Tab(
        selected = true,
        onClick = {},
        selectedContentColor = androidx.compose.ui.graphics.Color.Red,
        unselectedContentColor = androidx.compose.ui.graphics.Color.Gray,
        modifier = Modifier.padding(0.dp)
    ) {
        Column {
            Text("Hi")
        }
    }
}
