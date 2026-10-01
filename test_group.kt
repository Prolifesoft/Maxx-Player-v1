import java.util.regex.Pattern

fun main() {
    val titles = listOf(
        "Gibi S04 E08",
        "Gibi S04 E09",
        "Gibi S05 E01",
        "Tolgshow Çılgın Sezon S01 E01",
        "Tolgshow Çılgın Sezon S02 E01"
    )
    val pattern = Pattern.compile("^(.*?)\\s*S(\\d+)\\s*E(\\d+)(.*)$", Pattern.CASE_INSENSITIVE)
    val items = mutableListOf<Map<String, Any>>()
    for (title in titles) {
        val matcher = pattern.matcher(title)
        if (matcher.matches()) {
            items.add(mapOf(
                "title" to title,
                "seriesName" to matcher.group(1)!!.trim(),
                "season" to matcher.group(2)!!.toInt(),
                "episode" to matcher.group(3)!!.toInt()
            ))
        }
    }
    
    val grouped = items.groupBy { it["seriesName"] as String }
    for ((name, eps) in grouped) {
        println("Series: $name")
        val bySeason = eps.groupBy { it["season"] as Int }.toSortedMap()
        for ((s, e) in bySeason) {
            println("  Season $s: ${e.size} episodes")
        }
    }
}
