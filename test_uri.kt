import android.net.Uri

fun main() {
    val uri = Uri.parse("")
    println(uri.scheme)
    println(uri.path)
}
