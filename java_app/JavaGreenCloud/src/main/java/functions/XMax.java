package functions;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class XMax {

    public static String maxInList(String s) {
        String[] pairs = s.replace("(", "").replace(")", "").split(";");
        int m = -1;
        String key = "NONE";
        HashMap<String, Integer> d = new HashMap<String, Integer>();
        for (String pair : pairs) {
            String[] kv = pair.split(",");
            int i = Integer.parseInt(kv[1]);
            d.put(kv[0], i);
            if (m < i) {
                m = i;
                key = kv[0];
            }
        }
        return key + "," + m;
    }

    public static String getXMax(String path, String n) throws IOException {
        int nbmax = Integer.parseInt(n);
        if (nbmax < 1) {
            nbmax = 1;
        }
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String s = reader.readLine();
        StringBuilder keys = new StringBuilder();
        while (keys.toString().split(",").length <= nbmax) {
            String max = maxInList(s);
            keys.append("'"+max.split(",")[0] + "'" + ", ");
            s = s.replace("(" + max + ");", "").replace(";(" + max + ")", "");
        }
        reader.close();
        return "[" + keys.toString().substring(0, keys.toString().length() - 2) + "]";
    }

}
