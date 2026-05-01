package com.ricestoremanagement.service;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricestoremanagement.dto.ai.AiParsedOrder;

@Service
public class AiParsingService {
    private static final Logger log = LoggerFactory.getLogger(AiParsingService.class);

    private final RestClient openAiClient;
    private final RestClient geminiClient;
    private final ObjectMapper objectMapper;
    private final String provider;
    private final String openAiKey;
    private final String openAiModel;
    private final String geminiKey;
    private final String geminiModel;
    private final String systemPrompt;

    public AiParsingService(
            ObjectMapper objectMapper,
            @Value("${ai.provider:openai}") String provider,
            @Value("${ai.openai.base-url:${ai.api.base-url:https://api.openai.com/v1}}") String openAiBaseUrl,
            @Value("${ai.openai.key:${ai.api.key:}}") String openAiKey,
            @Value("${ai.openai.model:${ai.api.model:gpt-4o-mini}}") String openAiModel,
            @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String geminiBaseUrl,
            @Value("${ai.gemini.key:}") String geminiKey,
            @Value("${ai.gemini.model:gemini-1.5-flash}") String geminiModel,
            @Value("${ai.parse.system-prompt:Extract order fields and return JSON with keys rice_type, quantity, address. Use empty string for missing values.}")
                    String systemPrompt) {
        this.objectMapper = objectMapper;
        this.provider = provider;
        this.openAiKey = openAiKey;
        this.openAiModel = openAiModel;
        this.geminiKey = geminiKey;
        this.geminiModel = geminiModel;
        this.systemPrompt = systemPrompt;
        this.openAiClient = RestClient.builder().baseUrl(openAiBaseUrl).build();
        this.geminiClient = RestClient.builder().baseUrl(geminiBaseUrl).build();
    }

    public Optional<AiParsedOrder> parseOrder(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return Optional.empty();
        }
        if ("gemini".equalsIgnoreCase(provider)) {
            return parseWithGemini(messageText);
        }
        if (!"openai".equalsIgnoreCase(provider)) {
            log.warn("Unknown ai.provider value '{}'; falling back to OpenAI", provider);
        }
        return parseWithOpenAi(messageText);
    }

    private Optional<AiParsedOrder> parseWithOpenAi(String messageText) {
        if (openAiKey == null || openAiKey.trim().isEmpty()) {
            log.warn("OpenAI key not configured; skipping AI parsing");
            return Optional.empty();
        }

        AiChatCompletionRequest request = buildOpenAiRequest(messageText);

        try {
            AiChatCompletionResponse response = openAiClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiKey)
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
            log.warn("OpenAI parsing request failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AiParsedOrder> parseWithGemini(String messageText) {
        if (geminiKey == null || geminiKey.trim().isEmpty()) {
            log.warn("Gemini key not configured; skipping AI parsing");
            return Optional.empty();
        }

        GeminiGenerateRequest request = buildGeminiRequest(messageText);

        try {
            GeminiGenerateResponse response = geminiClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/" + geminiModel + ":generateContent")
                            .queryParam("key", geminiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GeminiGenerateResponse.class);

            if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
                return Optional.empty();
            }

            GeminiCandidate candidate = response.getCandidates().get(0);
            if (candidate == null || candidate.getContent() == null
                    || candidate.getContent().getParts() == null
                    || candidate.getContent().getParts().isEmpty()) {
                return Optional.empty();
            }

            String content = candidate.getContent().getParts().get(0).getText();
            return parseJson(content);
        } catch (RestClientException ex) {
            log.warn("Gemini parsing request failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private AiChatCompletionRequest buildOpenAiRequest(String messageText) {
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatMessage("system", systemPrompt));
        messages.add(new AiChatMessage("user", messageText));

        AiChatCompletionRequest request = new AiChatCompletionRequest();
        request.setModel(openAiModel);
        request.setMessages(messages);
        request.setTemperature(0.2);
        request.setResponseFormat(new AiResponseFormat("json_object"));
        return request;
    }

    private GeminiGenerateRequest buildGeminiRequest(String messageText) {
        GeminiGenerateRequest request = new GeminiGenerateRequest();

        GeminiContent systemInstruction = new GeminiContent(null,
                List.of(new GeminiPart(systemPrompt)));
        GeminiContent userContent = new GeminiContent("user",
                List.of(new GeminiPart(messageText)));

        request.setSystemInstruction(systemInstruction);
        request.setContents(List.of(userContent));
        request.setGenerationConfig(new GeminiGenerationConfig(0.2, "application/json"));
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

    private static class GeminiGenerateRequest {
        private List<GeminiContent> contents;
        @JsonProperty("systemInstruction")
        private GeminiContent systemInstruction;
        @JsonProperty("generationConfig")
        private GeminiGenerationConfig generationConfig;

        public List<GeminiContent> getContents() {
            return contents;
        }

        public void setContents(List<GeminiContent> contents) {
            this.contents = contents;
        }

        public GeminiContent getSystemInstruction() {
            return systemInstruction;
        }

        public void setSystemInstruction(GeminiContent systemInstruction) {
            this.systemInstruction = systemInstruction;
        }

        public GeminiGenerationConfig getGenerationConfig() {
            return generationConfig;
        }

        public void setGenerationConfig(GeminiGenerationConfig generationConfig) {
            this.generationConfig = generationConfig;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeminiGenerateResponse {
        private List<GeminiCandidate> candidates;

        public List<GeminiCandidate> getCandidates() {
            return candidates;
        }

        public void setCandidates(List<GeminiCandidate> candidates) {
            this.candidates = candidates;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeminiCandidate {
        private GeminiContent content;

        public GeminiContent getContent() {
            return content;
        }

        public void setContent(GeminiContent content) {
            this.content = content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeminiContent {
        private String role;
        private List<GeminiPart> parts;

        public GeminiContent() {
        }

        public GeminiContent(String role, List<GeminiPart> parts) {
            this.role = role;
            this.parts = parts;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<GeminiPart> getParts() {
            return parts;
        }

        public void setParts(List<GeminiPart> parts) {
            this.parts = parts;
        }
    }

    private static class GeminiPart {
        private String text;

        public GeminiPart() {
        }

        public GeminiPart(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    private static class GeminiGenerationConfig {
        private Double temperature;
        @JsonProperty("responseMimeType")
        private String responseMimeType;

        public GeminiGenerationConfig() {
        }

        public GeminiGenerationConfig(Double temperature, String responseMimeType) {
            this.temperature = temperature;
            this.responseMimeType = responseMimeType;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public String getResponseMimeType() {
            return responseMimeType;
        }

        public void setResponseMimeType(String responseMimeType) {
            this.responseMimeType = responseMimeType;
        }
    }
}
