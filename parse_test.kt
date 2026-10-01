import java.io.File

fun main() {
    val content = """#EXTM3U#EXTINF:-1 tvg-id="24 TV" tvg-name="[TR] Trt Spor FHD" tvg-logo="https://i.hizliresim.com/4hxpt28.png" group-title="TR SPOR KANALLARI ",[TR] Trt Spor FHD
http://fix.fixekran.xyz:8080/baki/W8NYgWCpSWjd/241980
#EXTINF:-1 tvg-id="24 TV" tvg-name="[TR] Trt Spor HD" tvg-logo="https://i.hizliresim.com/4hxpt28.png" group-title="TR SPOR KANALLARI ",[TR] Trt Spor HD
http://fix.fixekran.xyz:8080/baki/W8NYgWCpSWjd/241981"""

    var currentTitle = ""
    var currentLogo: String? = null
    var currentGroup: String? = null

    val lines = content.split("\n")
    for (l in lines) {
        var line = l.trim()
        
        // Handle the case where #EXTM3U is on the same line as #EXTINF
        if (line.startsWith("#EXTM3U#EXTINF:")) {
            line = line.substring(7)
        }
        
        if (line.startsWith("#EXTINF:")) {
            val logoMatch = "tvg-logo=\"([^\"]*)\"".toRegex().find(line)
            currentLogo = logoMatch?.groups?.get(1)?.value
            
            val groupMatch = "group-title=\"([^\"]*)\"".toRegex().find(line)
            currentGroup = groupMatch?.groups?.get(1)?.value
            
            val commaIndex = line.lastIndexOf(',')
            if (commaIndex != -1) {
                currentTitle = line.substring(commaIndex + 1).trim()
            } else {
                currentTitle = "Unknown Channel"
            }
            println("Parsed: Title='${currentTitle}', Logo='${currentLogo}', Group='${currentGroup}'")
        } else if (line.isNotEmpty() && !line.startsWith("#")) {
            println("Added Item: Title='${currentTitle}', Url='${line}'")
            currentTitle = ""
            currentLogo = null
            currentGroup = null
        }
    }
}
