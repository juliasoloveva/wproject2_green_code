import com.google.gson.Gson;
import com.google.gson.JsonObject;
import functions.*;
import org.bson.Document;
//rename functions like in the input file
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static functions.SinkAggregate.*;
import static functions.StorePrice.*;

public class Main {

     private static final String INPUT_FOLDER = "/data/input";
    private static final String OUTPUT_FOLDER = "/data/output";

    public static void main(String[] args) throws Exception {

        while (true) {
            // List files under the input folder
            ArrayList<File> files = new ArrayList<>(List.of(new File(INPUT_FOLDER).listFiles()));
            // If no file is present we wait for 5 seconds and look again
            if (files.isEmpty()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;

            }

            // We process the first file
            File file = files.get(0);
            try {
                Thread.sleep(5000); // make sure that file is completely uploaded
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            InputStream inputStream = new FileInputStream(file);

            String inputString = new String(inputStream.readAllBytes());

            Gson gson = new Gson();

            JsonObject commands = gson.fromJson(inputString, JsonObject.class);

            Path tmpPath = Files.createTempFile("temp_", ".txt");

            FileWriter fileWriter = new FileWriter(tmpPath.toFile());

            for (String key : commands.keySet()) {
                String command = commands.get(key).getAsJsonObject().get("type").getAsString();
                JsonObject arguments = commands.get(key).getAsJsonObject().get("arguments").getAsJsonObject();
                String output = "";
                switch (command) {
                    case "prime_numbers":
                        int param = Integer.valueOf(String.valueOf(arguments.get("n")));
                        output = String.valueOf(PrimeNumbers.primeNumbers(param));
                        break;
                    case "sum_prime_numbers":
                        int number = Integer.valueOf(String.valueOf(arguments.get("n")));
                        output = String.valueOf(PrimeNumbers.sumPrimeNumbers(number));
                        break;
                    case "clone_product":

                        int product_id = Integer.parseInt(arguments.get("product_id").getAsString());
                        int new_product_id = Integer.parseInt(arguments.get("new_product_id").getAsString());
                        double coef = Double.parseDouble(arguments.get("coef").getAsString());

                output = String.valueOf(StorePrice.cloneProduct(product_id, new_product_id, coef));

                        break;
                    case "delete_product":
                        int id_to_delete = Integer.parseInt(arguments.get("product_id").getAsString());
                                   output = StorePrice.deleteProduct(id_to_delete);
                        break;
                    case "sum_of_prices":
                        int prod_id = Integer.parseInt(arguments.get("product_id").getAsString());

                                     output = String.valueOf(StorePrice.sumOfPrices(prod_id));

                        break;
                    case "parse_transport_stream":
                        String stream_file = arguments.get("filename").getAsString();
                        output = PESPacketInfo.parseTransportStream(stream_file);
                        break;
                    case "cmd_fact":
                        int factor = Integer.valueOf(String.valueOf(arguments.get("n")));
                        output = Factorial.cmd_fact(factor);
                        break;
                    case "get_x_max":
                        String path = arguments.get("path").getAsString();
                        String n = arguments.get("n").getAsString();
                        output = XMax.getXMax(path, n);
                        break;
                    case "templating_dlms":
                        String filename_id = arguments.get("filename_id").getAsString();
                        String dt_start = arguments.get("dt_start").getAsString();
                        String dt_stop = arguments.get("dt_stop").getAsString();
                        output = GenerateDLMSCMD.templatingDlms(filename_id,dt_start,dt_stop).toString();
                        break;
                    case "decode_frame":

                        String frame = arguments.get("frame").getAsString();
                        output = DecryptFrame.decodeFrame(frame);
                        break;
                    case "sink_aggregation":
                        String json_data = String.valueOf(arguments.get("json_data"));
                        Document inputData = Document.parse(json_data);
                        output = sinkAggregation(inputData).toJson();
                        break;
                    default:
                        output = "Command not handled";
                }
                System.out.println(output);
               try {
                   fileWriter.write(key + " " + output + "\n");
                   fileWriter.flush();
               }catch (IOException e) {
                   e.printStackTrace(); // log any exceptions
               }
            }

            String file_name = file.getName();
            // Move the temporary output in the output folder
            Path outputPath = Paths.get(OUTPUT_FOLDER,
                    String.format("%s.txt", file_name.split("\\.")[0]));
            Files.move(tmpPath, outputPath);
            inputStream.close();
            fileWriter.close();
            // Once the file is processed delete it
            Files.deleteIfExists(file.toPath());

            files.remove(file);

        }
    }
}