package functions;
import java.text.SimpleDateFormat;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public class DecryptFrame {

    /**
     * In this case, it's a classic frame decoding problem
     * inputs : frame : a frame in hexa with type string
     * output: json decoded with no blank type string or the error message
     */

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final List<String> FOS_LIST = Arrays.asList("CC29", "23FE");

    public static long toUnsigned(String hexaString, long n) {
        int l = hexaString.length();
        long cmplt = Long.parseLong("F".repeat(l), 16);
        long cmplt2 = Long.parseLong("8" + "0".repeat(l - 1), 16);
        n = n & cmplt;
        return n | (-(n & cmplt2));
    }
    public static long decodeHexToDec(String hexaString) {
        int lenString = hexaString.length();
        String reversedHexaString = "";
        for (int i = lenString; i > 0; i -= 2) {
            reversedHexaString += hexaString.substring(i - 2, i);
        }
        return toUnsigned(hexaString, Long.parseLong(reversedHexaString, 16));
    }

    public static LocalDateTime decodeDate(String hexString) {
        int minute = Integer.parseInt(hexToBinary(hexString.substring(0, 2)).substring(2, 8), 2);
        int hour = Integer.parseInt(hexToBinary(hexString.substring(2, 4)).substring(3, 8), 2);
        int day = Integer.parseInt(hexToBinary(hexString.substring(4, 6)).substring(3, 8), 2);
        int month = Integer.parseInt(hexToBinary(hexString.substring(6, 8)).substring(4, 8), 2);
        int yearThousand = 1900 + 100 * Integer.parseInt(hexToBinary(hexString.substring(2, 4)).substring(1, 3), 2);
        int yearCut = Integer.parseInt(hexToBinary(hexString.substring(6, 8)).substring(0, 4) + hexToBinary(hexString.substring(4, 6)).substring(0, 3), 2);
        int year = yearThousand + yearCut;
        return LocalDateTime.of(year, month, day, hour, minute);
}

    public static String hexToBinary(String hex) {
        int decimal = Integer.parseInt(hex, 16);
        String binary = Long.toBinaryString(decimal);
        return String.format("%8s", binary).replace(' ', '0');
    }

    public static String frameToJson(String frame) {

        LocalDateTime date = decodeDate(frame.substring(106, 114));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("FOS", frame.substring(2, 6));
        result.put("FFC", frame.substring(6, 10));
        result.put("MetrologicalSerialNumber", frame.substring(10, 26));
        result.put("CustomerSerialNumber", frame.substring(26, 58));

        Map<String, Object> backflow = new LinkedHashMap<>();
        backflow.put("value", decodeHexToDec(frame.substring(58, 66)));
        backflow.put("unit", "m3");
        result.put("Backflow", backflow);

        Map<String, Object> signalStrength = new LinkedHashMap<>();
        signalStrength.put("value", decodeHexToDec(frame.substring(66, 70)));
        signalStrength.put("unit", "dbm");
        result.put("SignalStrenght", signalStrength);

        Map<String, Object> signalQuality = new LinkedHashMap<>();
        signalQuality.put("value", decodeHexToDec(frame.substring(70, 72)));
        signalQuality.put("unit", "dbm");
        result.put("SignalQuality", signalQuality);

        result.put("Alarms", frame.substring(72, 80));

        Map<String, Object> batteryRemaining = new LinkedHashMap<>();
        batteryRemaining.put("value", decodeHexToDec(frame.substring(80, 84)));
        batteryRemaining.put("unit", "days");
        result.put("Battery Remaining", batteryRemaining);

        Map<String, Object> operatingTime = new LinkedHashMap<>();
        operatingTime.put("value", decodeHexToDec(frame.substring(84, 88)));
        operatingTime.put("unit", "days");
        result.put("Operating Time", operatingTime);

        result.put("Customer Location", frame.substring(88, 104));
        result.put("Operator Specific Data", frame.substring(104, 106));

        Map<String, Object> dateTime = new LinkedHashMap<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
        LocalDateTime localDateTime = LocalDateTime.parse(formatter.format(date), formatter);

        dateTime.put("value", localDateTime.format(formatter));
        dateTime.put("storage", 1);
        result.put("Date_Time", dateTime);

        Map<String, Object> baseIndex = new LinkedHashMap<>();
        baseIndex.put("value", decodeHexToDec(frame.substring(114, 122)));
        baseIndex.put("unit", "m3");
        baseIndex.put("storage", 1);
        result.put("BaseIndex", baseIndex);

        List<Map<String, Object>> compactProfile = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            Map<String, Object> compactProfileData = new LinkedHashMap<>();
            compactProfileData.put("value", decodeHexToDec(frame.substring(128 + i * 4, 132 + i * 4)));
            compactProfileData.put("unit", "m3");
            compactProfileData.put("storage", 1);
            compactProfileData.put("date", localDateTime.minusMinutes(15 * (i + 1)).format(formatter));
            compactProfile.add(compactProfileData);
        }
        result.put("CompactProfile", compactProfile);
        Gson gson = new GsonBuilder()
             //   .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
        return gson.toJson(result);
    }
    public static String decodeFrame(String frame) {
        String str = "";
        if (frame.length() != 144) {
            str = "Invalid frame";
        } else if (!frame.substring(0, 2).equals("79")) {
            str = "Frame doesn't start with 79";
        } else if (!FOS_LIST.contains(frame.substring(2, 6))) {
            str = "Invalid FOS";
        } else {

            str = frameToJson(frame);
        }
        return str;
    }
}
