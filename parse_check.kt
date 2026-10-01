import java.io.File
import java.net.URL
import java.util.Scanner

enum class ItemType {
    LIVE, MOVIE, SERIES
}

data class M3uItem(
    val title: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val type: ItemType = ItemType.LIVE
)

fun main() {
    val items = mutableListOf<M3uItem>()
    val url = "http://fix.fixekran.xyz:8080/get.php?username=baki&password=W8NYgWCpSWjd&type=m3u_plus&output=mpegts"
    
    val text = URL(url).readText()
    var currentTitle = ""
    var currentGroup: String? = null
    
    val lines = text.split("\n")
    for (l in lines) {
        var line = l.trim()
        if (line.startsWith("#EXTINF:")) {
            val groupMatch = "group-title=\"([^\"]*)\"".toRegex().find(line)
            currentGroup = groupMatch?.groups?.get(1)?.value
            
            val commaIndex = line.lastIndexOf(',')
            if (commaIndex != -1) {
                currentTitle = line.substring(commaIndex + 1).trim()
            }
        } else if (line.isNotEmpty() && !line.startsWith("#")) {
            val type = when {
                line.contains("/series/") -> ItemType.SERIES
                line.contains("/movie/") || line.endsWith(".mkv") || line.endsWith(".mp4") || line.endsWith(".avi") -> ItemType.MOVIE
                else -> ItemType.LIVE
            }
            items.add(M3uItem(currentTitle, line, null, currentGroup, type))
            currentTitle = ""
            currentGroup = null
        }
    }
    
    val series = items.filter { it.type == ItemType.SERIES }
    println("Total items: ${items.size}")
    println("Series items: ${series.size}")
    if (series.isNotEmpty()) {
        println("Sample series:")
        series.take(5).forEach { println("- ${it.title} (${it.group})") }
    }
}
