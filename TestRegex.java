import java.util.regex.Pattern;
import java.util.regex.Matcher;
public class TestRegex {
    public static void main(String[] args) {
        String title = "Gibi 2. Sezon 5. Bölüm";
        Pattern pattern = Pattern.compile("^(.*?)\\s*S(\\d+)\\s*E(\\d+)(.*)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(title);
        System.out.println(matcher.matches());
    }
}
