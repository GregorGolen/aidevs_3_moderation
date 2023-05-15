import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;

public class Main {

    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        Properties prop = new Properties();
        try (InputStream input = Main.class.getResourceAsStream("/config.properties")) {
            prop.load(input);
        } catch (IOException e) {
            System.err.println("Error loading properties file: " + e.getMessage());
            return;
        }
        String apiKey = prop.getProperty("openai.api.key");

        String myKey = prop.getProperty("api.key");

        HttpRequest authRequest = HttpRequest.newBuilder()
                .uri(new URI("https://zadania.aidevs.pl/token/moderation"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.createObjectBuilder()
                        .add("apikey", myKey)
                        .build().toString()))
                .build();

        HttpResponse<String> authResponse = client.send(authRequest, HttpResponse.BodyHandlers.ofString());

        JsonObject jsonResponse = Json.createReader(new java.io.StringReader(authResponse.body())).readObject();
        String token = jsonResponse.getString("token");

        HttpRequest taskRequest = HttpRequest.newBuilder()
                .uri(new URI("https://zadania.aidevs.pl/task/" + token))
                .GET()
                .build();

        HttpResponse<String> taskResponse = client.send(taskRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println("Task response code: " + taskResponse.statusCode());

        String sentence = taskResponse.body();

        ObjectMapper objectMapper = new ObjectMapper();
        List<String> sentences = new ArrayList<>();
        try {
            JsonNode jsonNode = objectMapper.readTree(sentence);
            JsonNode inputNode = jsonNode.get("input");

            if (inputNode.isArray()) {
                for (JsonNode node : inputNode) {
                    sentences.add(node.asText());
                }
            }

            for (String sen : sentences) {
                System.out.println(sen);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<Integer> moderationResults = new ArrayList<>();

        for (String sen : sentences) {
            HttpRequest moderationRequest = HttpRequest.newBuilder()
                    .uri(new URI("https://api.openai.com/v1/moderations"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(Json.createObjectBuilder()
                            .add("input", sen)
                            .build().toString()))
                    .build();

            HttpResponse<String> moderationResponse = client.send(moderationRequest, HttpResponse.BodyHandlers.ofString());

            if (moderationResponse.statusCode() == 200) {
                try (JsonReader reader = Json.createReader(new java.io.StringReader(moderationResponse.body()))) {
                    JsonObject responseJson = reader.readObject();
                    if (responseJson.containsKey("results")) {
                        JsonObject results = responseJson.getJsonArray("results").getJsonObject(0);
                        moderationResults.add(results.getBoolean("flagged") ? 1 : 0);
                    } else {
                        System.out.println("Error in response: " + responseJson.toString());
                    }
                }
            } else {
                System.out.println("Error sending moderation request: " + moderationResponse.statusCode());
            }
        }

        System.out.println("Moderation results: " + moderationResults);
        List<String> moderationResultsString = moderationResults.stream().map(String::valueOf).collect(Collectors.toList());

        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        moderationResultsString.forEach(arrayBuilder::add);

        JsonObject answer = Json.createObjectBuilder().add("answer", arrayBuilder.build()).build();

        HttpRequest answerRequest = HttpRequest.newBuilder()
                .uri(new URI("https://zadania.aidevs.pl/answer/" + token))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(answer.toString()))
                .build();

        HttpResponse<String> answerResponse = client.send(answerRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println("Answer response code: " + answerResponse.statusCode());
    }
}

