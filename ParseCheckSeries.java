import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class ParseCheckSeries {
    public static void main(String[] args) throws Exception {
        URL url = new URL("http://fix.fixekran.xyz:8080/get.php?username=baki&password=W8NYgWCpSWjd&type=m3u_plus&output=mpegts");
        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
        
        String inputLine;
        int count = 0;
        
        while ((inputLine = in.readLine()) != null) {
            String line = inputLine.trim();
            if (line.isEmpty() || !line.startsWith("#EXTINF:")) continue;
            
            String group = "";
            String title = "";
            java.util.regex.Matcher mGroup = java.util.regex.Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
            if (mGroup.find()) group = mGroup.group(1);
            
            int commaIndex = line.lastIndexOf(',');
            if (commaIndex != -1) {
                title = line.substring(commaIndex + 1).trim();
            }
            
            String nextLine = in.readLine();
            if (nextLine != null && nextLine.contains("/series/")) {
                System.out.println("Group: " + group + " | Title: " + title);
                count++;
                if (count >= 20) break;
            }
        }
        in.close();
    }
}
