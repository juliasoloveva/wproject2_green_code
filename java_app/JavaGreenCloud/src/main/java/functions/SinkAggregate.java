package functions;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.List;
import java.util.ArrayList;

public class SinkAggregate {

    private static final int RSSI_THRESHOLD = -70;
    private static final String MONGO_DB_HOST = "vm";
    private static final int MONGO_DB_PORT = 27017;
    private static final String MONGO_DB_USERNAME = "mongodb";
    private static final String MONGO_DB_PASSWORD = "mongodb";
    private static final String MONGO_DB_DATABASE_NAME = "dataLake";
    private static final String MONGO_DB_COLLECTION_NAME = "wifi";

    public static Document sinkAggregation(Document json_data) {

        Document aggregate_data = new Document("identifier", null)
                .append("manufacturerName", null)
                .append("startTime", null)
                .append("endTime", null)
                .append("wifiAggregate", new Document("deviceType", null)
                        .append("minRSSI", null)
                        .append("avgRSSI", null)
                        .append("maxRSSI", null)
                        .append("countBandChange", null))
                .append("anomalies_report", new ArrayList<>());

        aggregate_data.put("identifier", json_data.get("info", Document.class).get("identifier"));
        aggregate_data.put("manufacturerName", json_data.get("info", Document.class).get("manufacturerName"));
        aggregate_data.put("startTime", findMin(json_data.getList("wifiData", Document.class), "eventTime"));
        aggregate_data.put("endTime", findMax(json_data.getList("wifiData", Document.class), "eventTime"));
        aggregate_data.get("wifiAggregate", Document.class).put("deviceType", json_data.getList("wifiData", Document.class).get(0).get("deviceType"));
        aggregate_data.get("wifiAggregate", Document.class).put("countBandChange", countValueChange(json_data.getList("wifiData", Document.class), "connection"));
        aggregate_data.get("wifiAggregate", Document.class).put("minRSSI", findMin(json_data.getList("wifiData", Document.class), "rssi"));
        aggregate_data.get("wifiAggregate", Document.class).put("maxRSSI", findMax(json_data.getList("wifiData", Document.class), "rssi"));
        aggregate_data.get("wifiAggregate", Document.class).put("avgRSSI", calculateAvg(json_data.getList("wifiData", Document.class), "rssi"));
        aggregate_data.put("anomalies_report", detectAnomalyMin(json_data.getList("wifiData", Document.class), "rssi", RSSI_THRESHOLD));

        String identifier_data = json_data.get("info", Document.class).getString("identifier");

        try (MongoClient mongo_server = new MongoClient(new MongoClientURI(String.format("mongodb://%s:%s@%s:%d/", MONGO_DB_USERNAME, MONGO_DB_PASSWORD, MONGO_DB_HOST, MONGO_DB_PORT)))) {
            insertDataMongo(mongo_server, aggregate_data);

            Document result_aggregate_data = findDataMongo(mongo_server, identifier_data);
            return result_aggregate_data;
        } catch (Exception e) {
            return null;
        }
    }

    public static Double findMin(List<Document> array, String key) {
        if (array.size() > 0) {
            Double min = ((Integer)array.get(0).get(key)).doubleValue();
            for (int i = 1; i < array.size(); i++) {
                if (array.get(i).get(key) != null) {
                    if (((Integer)array.get(i).get(key)).doubleValue() < min) {
                        min = ((Integer)array.get(i).get(key)).doubleValue();
                    }
                }
            }
            return min;
        }
        return null;
    }

    public static Double findMax(List<Document> array, String key) {
        if (array.size() > 0) {
            Double max = ((Integer)array.get(0).get(key)).doubleValue();
            for (int i = 1; i < array.size(); i++) {
                if (array.get(i).get(key) != null) {
                    if (((Integer)array.get(i).get(key)).doubleValue()> max) {
                        max = ((Integer)array.get(i).get(key)).doubleValue();
                    }
                }
            }
            return max;
        }
        return null;
    }

    public static Integer countValueChange(List<Document> array, String key) {
        int counter = 0;
        if (array.size() > 0) {
            Object refValue = array.get(0).get(key);
            for (int i = 1; i < array.size(); i++) {
                if (!array.get(i).get(key).equals(refValue)) {
                    counter++;
                    refValue = array.get(i).get(key);
                }
            }
            return counter;
        }
        return null;
    }
    public static Integer calculateAvg(List<Document> array, String key) {
        if (array.size() > 0) {
            Integer sum = 0;
            for (int i = 0; i < array.size(); i++) {
                sum += array.get(i).getInteger(key);
            }
            return sum / array.size();
        }
        return null;
    }

    public static List<Document> detectAnomalyMin(List<Document> array, String key, Integer threshold) {
        List<Document> arrayAnomaly = new ArrayList<>();
        if (array.size() > 0) {
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i).getInteger(key) < threshold) {
                    Document anomalyReport = new Document();
                    anomalyReport.append("eventTime", array.get(i).get("eventTime"));
                    anomalyReport.append("deviceType", array.get(i).get("deviceType"));
                    anomalyReport.append("connection", array.get(i).get("connection"));
                    anomalyReport.append("rssi", array.get(i).get("rssi"));
                    arrayAnomaly.add(anomalyReport);
                }
            }
        }
        return arrayAnomaly;
    }
    public static boolean insertDataMongo(MongoClient mongoServer, Document jsonInsert) {
        try {
            MongoDatabase database = mongoServer.getDatabase(MONGO_DB_DATABASE_NAME);
            MongoCollection<Document> collection = database.getCollection(MONGO_DB_COLLECTION_NAME);
            collection.insertOne(jsonInsert);
            return true;
        } catch (Exception e) {
            System.out.println("Cannot put data into MongoDB");
            return false;
        }
    }
    public static Document findDataMongo(MongoClient mongoServer, String identifier) {
        Document result = new Document();
        try {
            MongoDatabase database = mongoServer.getDatabase(MONGO_DB_DATABASE_NAME);
            MongoCollection<Document> collection = database.getCollection(MONGO_DB_COLLECTION_NAME);
            result = collection.find(new Document("identifier", identifier)).projection(new Document("_id", 0)).first();
        } catch (Exception e) {
            System.out.println("Cannot find data into MongoDB");
        }
        return result;
    }
}
