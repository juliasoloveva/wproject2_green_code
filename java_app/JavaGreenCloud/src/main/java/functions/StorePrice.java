package functions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class StorePrice {

        private static final String LIST_PRODUCT_URL = "http://vm:8000/product_items";
        private static final String ADD_PRODUCT_URL = "http://vm:8000/product_item";
        private static final String DELETE_PRODUCT_URL = "http://vm:8000/product";

        private static final Gson gson = new Gson();

        public static int cloneProduct(int productId, int newProductId, double coefficient) throws Exception {
            String itemsJson = sendGetRequest(LIST_PRODUCT_URL + "/" + productId);
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> items = gson.fromJson(itemsJson, listType);

            for (Map<String, Object> item : items) {
                item.remove("id");
                item.put("product_id", newProductId);
                item.put("price", ((double)item.get("price")) * coefficient);
                String itemJson = gson.toJson(item);
                sendPutRequest(ADD_PRODUCT_URL, itemJson);
            }

            return items.size();
        }

        public static long sumOfPrices(int productId) throws Exception {
            String itemsJson = sendGetRequest(LIST_PRODUCT_URL + "/" + productId);
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> items = gson.fromJson(itemsJson, listType);

            double sum = 0;
            for (Map<String, Object> item : items) {
                sum += ((double)item.get("price"));
            }

            return Math.round(sum);
        }


        private static String sendGetRequest(String urlString) throws Exception {
            URL url = new URL(urlString);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return response.toString();
        }

        private static String sendPutRequest(String urlString, String json) throws Exception {
            URL url = new URL(urlString);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("PUT");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
            out.write(json);
            out.flush();
            out.close();

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return response.toString();
        }

    private static String sendDeleteRequest(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("DELETE");

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }

    public static String deleteProduct(int productId) throws Exception {
        return sendDeleteRequest(DELETE_PRODUCT_URL + "/" + productId);
    }
}
