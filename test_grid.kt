import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Test() {
    LazyVerticalGrid(columns = GridCells.Fixed(2)) {
        item {
            Text("1")
            Spacer(Modifier)
        }
    }
}
