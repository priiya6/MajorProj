package com.opssentinel.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Thin wrapper around the Gemini API's REST endpoints — no SDK dependency,
 * just java.net.http.HttpClient (built into the JDK) and Jackson (already
 * on the classpath via spring-boot-starter-web).
 *
 * Uses the classic generateContent endpoint (not the newer Interactions
 * API) — Google's own docs recommend generateContent for stable production
 * use, which matters more here than access to the newest beta features.
 */
@Component
public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${opssentinel.gemini.api-key}")
    private String apiKey;

    @Value("${opssentinel.gemini.model}")
    private String generationModel;

    @Value("${opssentinel.gemini.embedding-model}")
    private String embeddingModel;

    /** Calls embedContent and returns the embedding as a plain float[]. */
    public float[] embedContent(String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode content = body.putObject("content");
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + embeddingModel + ":embedContent"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Gemini embedContent failed (" + response.statusCode() + "): " + response.body());
            }

            JsonNode valuesNode = objectMapper.readTree(response.body()).path("embedding").path("values");
            float[] embedding = new float[valuesNode.size()];
            for (int i = 0; i < valuesNode.size(); i++) {
                embedding[i] = (float) valuesNode.get(i).asDouble();
            }
            return embedding;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini call interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }

    /**
     * Calls generateContent with a JSON response schema, forcing the model
     * to return structured JSON matching the given schema instead of free
     * text. Returns the parsed JSON response body (already unwrapped from
     * Gemini's candidates/content/parts envelope).
     */
    public JsonNode generateStructured(String prompt, ObjectNode responseSchema) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ArrayNode parts = contents.addObject().putArray("parts");
            parts.addObject().put("text", prompt);

            ObjectNode generationConfig = body.putObject("generationConfig");
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.set("responseSchema", responseSchema);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + generationModel + ":generateContent"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Gemini generateContent failed (" + response.statusCode() + "): " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String jsonText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            return objectMapper.readTree(jsonText);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to call Gemini generateContent", e);
        }
    }
    /**
     * Calls generateContent with no response schema — plain free-text
     * answer, for conversational follow-up questions rather than
     * structured pipeline output. Used by the "ask about this incident"
     * feature.
     */
    public String generateFreeText(String prompt) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ArrayNode parts = contents.addObject().putArray("parts");
            parts.addObject().put("text", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + generationModel + ":generateContent"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Gemini generateContent failed (" + response.statusCode() + "): " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to call Gemini generateContent", e);
        }
    }
}
