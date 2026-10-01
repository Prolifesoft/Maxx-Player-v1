public class RegexCheck {
    public static void main(String[] args) {
        String[] titles = {
            "İlginç Bazı Olaylar S01 E01",
            "The Walking Dead S11E24",
            "Game of Thrones S8 E06",
            "Some Show S02E1",
            "Show S1 E1"
        };
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(.*?)\\s*S(\\d+)\\s*E(\\d+)(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE);
        
        for (String title : titles) {
            java.util.regex.Matcher m = pattern.matcher(title);
            if (m.matches()) {
                System.out.println("Title: " + title);
                System.out.println("  Series Name: " + m.group(1));
                System.out.println("  Season: " + m.group(2));
                System.out.println("  Episode: " + m.group(3));
            } else {
                System.out.println("No match for: " + title);
            }
        }
    }
}
