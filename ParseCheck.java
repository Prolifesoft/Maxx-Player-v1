import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class ParseCheck {
    public static void main(String[] args) throws Exception {
        URL url = new URL("http://fix.fixekran.xyz:8080/get.php?username=baki&password=W8NYgWCpSWjd&type=m3u_plus&output=mpegts");
        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
        
        String inputLine;
        int total = 0;
        int series = 0;
        int movie = 0;
        int live = 0;
        
        while ((inputLine = in.readLine()) != null) {
            String line = inputLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            total++;
            if (line.contains("/series/")) {
                series++;
            } else if (line.contains("/movie/") || line.endsWith(".mkv") || line.endsWith(".mp4") || line.endsWith(".avi")) {
                movie++;
            } else {
                live++;
            }
        }
        in.close();
        
        System.out.println("Total: " + total);
        System.out.println("Series: " + series);
        System.out.println("Movies: " + movie);
        System.out.println("Live: " + live);
    }
}
