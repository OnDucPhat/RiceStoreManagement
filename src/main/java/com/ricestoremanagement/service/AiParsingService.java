package com.ricestoremanagement.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricestoremanagement.dto.ai.AiParsedOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AiParsingService {
    private static final Logger log = LoggerFactory.getLogger(AiParsingService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;

    public AiParsingService(
            ObjectMapper objectMapper,
            @Value("${ai.api.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${ai.api.key:}") String apiKey,
            @Value("${ai.api.model:gpt-4o-mini}") String model,
            @Value("${ai.parse.system-prompt:Extract order fields and return JSON with keys rice_type, quantity, address. Use empty string for missing values.}")
                    String systemPrompt) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Optional<AiParsedOrder> parseOrder(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return Optional.empty();
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("AI API key not configured; skipping AI parsing");
            return Optional.empty();
        }

        AiChatCompletionRequest request = buildRequest(messageText);

        try {
            AiChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(AiChatCompletionResponse.class);

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                return Optional.empty();
            }

            AiChatCompletionChoice choice = response.getChoices().get(0);
            if (choice == null || choice.getMessage() == null) {
                return Optional.empty();
            }
            String content = choice.getMessage().getContent();
            return parseJson(content);
        } catch (RestClientException ex) {
            log.warn("AI parsing request failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private AiChatCompletionRequest buildRequest(String messageText) {
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatMessage("system", systemPrompt));
        messages.add(new AiChatMessage("user", messageText));

        AiChatCompletionRequest request = new AiChatCompletionRequest();
        request.setModel(model);
        request.setMessages(messages);
        request.setTemperature(0.2);
        request.setResponseFormat(new AiResponseFormat("json_object"));
        return request;
    }

    private Optional<AiParsedOrder> parseJson(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Optional.empty();
        }

        String json = extractJsonObject(content);
        try {
            AiParsedOrder parsed = objectMapper.readValue(json, AiParsedOrder.class);
            return Optional.of(parsed);
        } catch (Exception ex) {
            log.warn("Unable to parse AI response: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content.trim();
    }

    private static class AiChatCompletionRequest {
        private String model;
        private List<AiChatMessage> messages;
        private Double temperature;
        @JsonProperty("response_format")
        private AiResponseFormat responseFormat;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<AiChatMessage> getMessages() {
            return messages;
        }

        public void setMessages(List<AiChatMessage> messages) {
            this.messages = messages;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public AiResponseFormat getResponseFormat() {
            return responseFormat;
        }

        public void setResponseFormat(AiResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AiChatMessage {
        private String role;
        private String content;

        public AiChatMessage() {
        }

        public AiChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    private static class AiResponseFormat {
        private String type;

        public AiResponseFormat() {
        }

        public AiResponseFormat(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AiChatCompletionResponse {
        private List<AiChatCompletionChoice> choices;

        public List<AiChatCompletionChoice> getChoices() {
            return choices;
        }

        public void setChoices(List<AiChatCompletionChoice> choices) {
            this.choices = choices;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AiChatCompletionChoice {
        private AiChatMessage message;

        public AiChatMessage getMessage() {
            return message;
        }

        public void setMessage(AiChatMessage message) {
            this.message = message;
        }
    }
}
