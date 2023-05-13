package functions;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


public class GenerateDLMSCMD {

    /**
     * In this case you need to produce n files from a template, list of id and two dates.
     * dlms is a standard of communication in energy with iot object (smart meter)
     * You need to replace the id of the template by the one in file and change the start and the stop date.
     * filename_id : filename of the the list of id
     * dt_start : a date in format YYYY-MM-DDTHH:MM:SS
     * dt_stop : a date in format YYYY-MM-DDTHH:MM:SS
     * return the list of md5 for each xml string generated in list format
     */
    public static List<String> templatingDlms(String filenameId, String dtStart, String dtStop)
            throws IOException, NoSuchAlgorithmException {
        BufferedReader file = new BufferedReader(new FileReader("/data/media/" + filenameId));
        String templateName = "/data/media/templating_tpl.xml";

        String tpl = new String(Files.readAllBytes(Paths.get(templateName)), StandardCharsets.UTF_8);

        String dStart = "2019-08-16T13:35:00";
        String dEnd = "2019-08-16T13:35:00";

        if (dtStart.length() >= 4) {
            dStart = dtStart;
        }
        if (dtStop.length() >= 5) {
            dEnd = dtStop;
        }

        List<String> md5s = new ArrayList<>();

        String line;
        while ((line = file.readLine()) != null) {
            String newId = line.replaceAll("\\n", "");
            String prefix = "Activation_2.76";

            String s = tpl.replaceAll("<devID>METER(.+?)</devID>", "<devID>" + newId + "</devID>");
            s = s.replaceAll("taskId=\"" + prefix + "\"", "taskId=\"" + prefix + "_" + newId + "\"");
            s = s.replaceAll("<start>[^<]+</start>", "<start>" + dStart + "</start>");
            s = s.replaceAll("<stop>[^<]+</stop>", "<stop>" + dEnd + "</stop>");

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] hash = md5.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            md5s.add(sb.toString());
        }

        return md5s;
    }
}

