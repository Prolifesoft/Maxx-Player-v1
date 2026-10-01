import java.util.regex.Pattern

fun main() {
    val titles = listOf(
        "Gibi S04 E08",
        "Çukur 1. Sezon 5. Bölüm",
        "Yargı 2.Sezon 10.Bölüm",
        "TR | Yargı Sezon 2 Bölüm 10"
    )
    val p1 = Pattern.compile("^(.*?)\\s*S(\\d+)\\s*E(\\d+)(.*)$", Pattern.CASE_INSENSITIVE)
    val p2 = Pattern.compile("^(.*?)\\s+(\\d+)\\.?\\s*Sezon\\s+(\\d+)\\.?\\s*Bölüm(.*)$", Pattern.CASE_INSENSITIVE)
    val p3 = Pattern.compile("^(.*?)\\s*Sezon\\s*(\\d+)\\s*Bölüm\\s*(\\d+)(.*)$", Pattern.CASE_INSENSITIVE)
    
    for (t in titles) {
        var m = p1.matcher(t)
        if (m.matches()) {
            println("P1 matched $t -> ${m.group(1)} S${m.group(2)} E${m.group(3)}")
            continue
        }
        m = p2.matcher(t)
        if (m.matches()) {
            println("P2 matched $t -> ${m.group(1)} S${m.group(2)} E${m.group(3)}")
            continue
        }
        m = p3.matcher(t)
        if (m.matches()) {
            println("P3 matched $t -> ${m.group(1)} S${m.group(2)} E${m.group(3)}")
            continue
        }
        println("FAILED: $t")
    }
}
