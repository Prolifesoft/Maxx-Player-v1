import java.util.regex.Pattern

fun main() {
    val titles = listOf(
        "İlginç Bazı Olaylar S01 E01",
        "I Am a Killer S02 E09",
        "Süper Baba S01 E61",
        "S01 E01",
        "Show S1E1",
        "TR | Show S02 E01",
        "[DE] Pokémon Horizonte: Die Serie S04 E17"
    )
    val pattern = Pattern.compile("^(.*?)\\s*S(\\d+)\\s*E(\\d+)(.*)$", Pattern.CASE_INSENSITIVE)
    for (title in titles) {
        val matcher = pattern.matcher(title)
        if (matcher.matches()) {
            println("MATCHED: '${title}' -> Series: '${matcher.group(1)?.trim()}', Season: ${matcher.group(2)}, Episode: ${matcher.group(3)}")
        } else {
            println("FAILED: '${title}'")
        }
    }
}
