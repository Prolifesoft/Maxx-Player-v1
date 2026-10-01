import java.io.File
import java.util.regex.Pattern

fun main() {
    var totalSupernatural = 0
    var supernaturalSeason1 = 0
    File("playlist.txt").forEachLine { line ->
        if (line.contains("Supernatural S")) {
            totalSupernatural++
            if (line.contains("Supernatural S01")) {
                supernaturalSeason1++
            }
        }
    }
    println("Total Supernatural: $totalSupernatural, Season 1: $supernaturalSeason1")
}
