import java.io.BufferedReader
import java.io.StringReader

fun main() {
    val text = "#EXTM3U#EXTINF:-1 tvg-id=\"24 TV\" tvg-name=\"[TR] Trt Spor FHD\" tvg-logo=\"https://i.hizliresim.com/4hxpt28.png\" group-title=\"TR SPOR KANALLARI \",[TR] Trt Spor FHD\rhttp://fix.fixekran.xyz:8080/baki/W8NYgWCpSWjd/241980\r#EXTINF:-1"
    val reader = BufferedReader(StringReader(text))
    var line = reader.readLine()
    while(line != null) {
        println("LINE: $line")
        line = reader.readLine()
    }
}
