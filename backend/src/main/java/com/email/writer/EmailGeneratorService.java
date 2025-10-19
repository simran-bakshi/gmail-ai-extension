package com.email.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class EmailGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(EmailGeneratorService.class);

    private final WebClient webClient;
    private final String geminiApiUrl;
    private final String geminiApiKey;

    public EmailGeneratorService(WebClient.Builder webClientBuilder,
                                 @Value("${gemini.api.url}") String geminiApiUrl,
                                 @Value("${gemini.api.key}") String geminiApiKey) {
        this.webClient = webClientBuilder.build();
        this.geminiApiUrl = geminiApiUrl;
        this.geminiApiKey = geminiApiKey;

        // Debug logging
        logger.info("Gemini API URL: {}", geminiApiUrl);
        logger.info("Gemini API Key exists: {}", geminiApiKey != null && !geminiApiKey.isEmpty());
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        try {
            // Validate API key
            if (geminiApiKey == null || geminiApiKey.isEmpty() || geminiApiKey.startsWith("${")) {
                return "Error: GEMINI_KEY environment variable is not set";
            }

            // Build a prompt
            String prompt = buildPrompt(emailRequest);
            logger.info("Generated prompt: {}", prompt);

            // Craft a request
            Map<String, Object> requestBody = Map.of(
                    "contents", new Object[]{
                            Map.of("parts", new Object[]{
                                    Map.of("text", prompt)
                            })
                    }
            );

            logger.info("Request body: {}", requestBody);

            // Construct full URL
            String fullUrl = geminiApiUrl.trim() + "?key=" + geminiApiKey.trim();
            logger.info("Full API URL: {}", fullUrl);

            // Do request and get response
            String response = webClient.post()
                    .uri(fullUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        logger.error("API Error - Body: {}", body);
                                        return Mono.error(new RuntimeException("API Error: " + body));
                                    }))
                    .bodyToMono(String.class)
                    .block();

            logger.info("API Response received: {}", response);

            // Extract & return Response
            return extractResponseContent(response);
        } catch (Exception e) {
            logger.error("Error generating email reply: ", e);
            return "Error generating email reply: " + e.getMessage();
        }
    }

    private String extractResponseContent(String response) {
        try {
            if (response == null || response.isEmpty()) {
                logger.warn("Empty response from API");
                return "Error: Empty response from API";
            }

            logger.info("Parsing response: {}", response);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);

            JsonNode candidatesNode = rootNode.path("candidates");
            if (candidatesNode.isMissingNode() || candidatesNode.isEmpty()) {
                logger.warn("No candidates in response");
                return "Error: No candidates in response";
            }

            JsonNode textNode = candidatesNode
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text");

            if (textNode.isMissingNode()) {
                logger.warn("Invalid response format - missing text node");
                return "Error: Invalid response format";
            }

            String result = textNode.asText();
            logger.info("Successfully extracted text: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("Error processing response: ", e);
            return "Error processing request: " + e.getMessage();
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a professional email reply for the following email content. Please don't generate a subject line.");

        if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
            prompt.append(" Use a ").append(emailRequest.getTone()).append(" tone.");
        }

        prompt.append("\nOriginal email:\n").append(emailRequest.getEmailContent());
        return prompt.toString();
    }
}