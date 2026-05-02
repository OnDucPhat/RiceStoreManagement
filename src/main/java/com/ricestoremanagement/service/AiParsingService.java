package com.ricestoremanagement.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricestoremanagement.dto.ai.AiChatbotResult;
import com.ricestoremanagement.dto.ai.AiParsedOrder;
import com.ricestoremanagement.model.RiceProduct;

@Service
public class AiParsingService {
    private static final Logger log = LoggerFactory.getLogger(AiParsingService.class);
    private static final String DEFAULT_CHATBOT_PROMPT = """
            You are a friendly Vietnamese AI assistant for a rice store.
            Classify each customer message and return only JSON with these keys:
            intent, rice_type, quantity, address, customer_phone, reply.
            intent must be ORDER_CREATE when the user wants to buy/order rice.
            intent must be GENERAL_CHAT for greetings, questions, and non-order conversation.
            Use empty strings for missing order fields.
            If ORDER_CREATE is missing rice_type, quantity, or address, reply in natural Vietnamese asking only for the missing information.
            If ORDER_CREATE is complete, reply in natural Vietnamese confirming the order details.
            If GENERAL_CHAT, reply as a helpful rice store assistant in Vietnamese.
            When recommending rice, use only the provided rice catalog. Mention prices from the catalog when useful.
            Keep reply concise, warm, and practical.
            """;

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
            @Value("${ai.timeout.connect-ms:120000}") long aiConnectTimeoutMs,
            @Value("${ai.timeout.read-ms:120000}") long aiReadTimeoutMs,
            @Value("${ai.parse.system-prompt:Extract order fields and return JSON with keys rice_type, quantity, address. Use empty string for missing values.}")
                    String systemPrompt) {
        this.objectMapper = objectMapper;
        this.provider = provider;
        this.openAiKey = openAiKey;
        this.openAiModel = openAiModel;
        this.geminiKey = geminiKey;
        this.geminiModel = geminiModel;
        this.systemPrompt = systemPrompt;
        this.openAiClient = buildRestClient(openAiBaseUrl, aiConnectTimeoutMs, aiReadTimeoutMs);
        this.geminiClient = buildRestClient(geminiBaseUrl, aiConnectTimeoutMs, aiReadTimeoutMs);
    }

    public Optional<AiChatbotResult> chat(String messageText) {
        return chat(messageText, List.of());
    }

    public Optional<AiChatbotResult> chat(String messageText, List<RiceProduct> riceCatalog) {
        return chat(messageText, riceCatalog, "");
    }

    public Optional<AiChatbotResult> chat(
            String messageText,
            List<RiceProduct> riceCatalog,
            String conversationContext) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return Optional.empty();
        }
        long startNs = System.nanoTime();
        Optional<AiChatbotResult> result;
        if ("gemini".equalsIgnoreCase(provider)) {
            result = chatWithGemini(messageText, riceCatalog, conversationContext);
        } else {
            if (!"openai".equalsIgnoreCase(provider)) {
                log.warn("Unknown ai.provider value '{}'; falling back to OpenAI", provider);
            }
            result = chatWithOpenAi(messageText, riceCatalog, conversationContext);
        }
        log.info("Messenger timing ai_provider={} durationMs={} success={}",
                provider,
                elapsedMs(startNs),
                result.isPresent());
        return result;
    }

    public Optional<AiParsedOrder> parseOrder(String messageText) {
        return chat(messageText).map(this::toParsedOrder);
    }

    private Optional<AiChatbotResult> chatWithOpenAi(
            String messageText,
            List<RiceProduct> riceCatalog,
            String conversationContext) {
        if (openAiKey == null || openAiKey.trim().isEmpty()) {
            log.warn("OpenAI key not configured; skipping AI parsing");
            return Optional.empty();
        }

        Map<String, Object> request = buildOpenAiRequest(messageText, riceCatalog, conversationContext);

        try {
            AiChatCompletionResponse response = sendOpenAiRequest(request);

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                log.warn("OpenAI parsing returned no choices");
                return Optional.empty();
            }

            AiChatCompletionChoice choice = response.getChoices().get(0);
            if (choice == null || choice.getMessage() == null) {
                log.warn("OpenAI parsing returned an empty message");
                return Optional.empty();
            }
            String content = choice.getMessage().getContent();
            return parseJson(content);
        } catch (HttpClientErrorException ex) {
            return handleOpenAiHttpError(ex);
        } catch (RestClientException ex) {
            log.warn("OpenAI parsing request failed type={} message={}", ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AiChatbotResult> chatWithGemini(
            String messageText,
            List<RiceProduct> riceCatalog,
            String conversationContext) {
        if (geminiKey == null || geminiKey.trim().isEmpty()) {
            log.warn("Gemini key not configured; skipping AI parsing");
            return Optional.empty();
        }

        GeminiGenerateRequest request = buildGeminiRequest(messageText, riceCatalog, conversationContext);

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

    private Map<String, Object> buildOpenAiRequest(
            String messageText,
            List<RiceProduct> riceCatalog,
            String conversationContext) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", chatbotPrompt(riceCatalog, conversationContext)));
        messages.add(Map.of("role", "user", "content", messageText));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", openAiModel);
        request.put("messages", messages);
        request.put("temperature", 0.2);
        return request;
    }

    private AiChatCompletionResponse sendOpenAiRequest(Map<String, Object> request) {
        return openAiClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiKey)
                .body(request)
                .retrieve()
                .body(AiChatCompletionResponse.class);
    }

    private RestClient buildRestClient(String baseUrl, long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private Optional<AiChatbotResult> handleOpenAiHttpError(HttpClientErrorException ex) {
        log.warn("OpenAI parsing request failed: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return Optional.empty();
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }

    private GeminiGenerateRequest buildGeminiRequest(
            String messageText,
            List<RiceProduct> riceCatalog,
            String conversationContext) {
        GeminiGenerateRequest request = new GeminiGenerateRequest();

        GeminiContent systemInstruction = new GeminiContent(null,
                List.of(new GeminiPart(chatbotPrompt(riceCatalog, conversationContext))));
        GeminiContent userContent = new GeminiContent("user",
                List.of(new GeminiPart(messageText)));

        request.setSystemInstruction(systemInstruction);
        request.setContents(List.of(userContent));
        request.setGenerationConfig(new GeminiGenerationConfig(0.2, "application/json"));
        return request;
    }

    private Optional<AiChatbotResult> parseJson(String content) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("AI response content is empty");
            return Optional.empty();
        }

        String json = extractJsonObject(content);
        try {
            AiChatbotResult result = objectMapper.readValue(json, AiChatbotResult.class);
            return Optional.of(result);
        } catch (Exception ex) {
            log.warn("Unable to parse AI response: {} contentSnippet={}", ex.getMessage(), snippet(content));
            return Optional.empty();
        }
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300);
    }

    private String chatbotPrompt(List<RiceProduct> riceCatalog) {
        return chatbotPrompt(riceCatalog, "");
    }

    private String chatbotPrompt(List<RiceProduct> riceCatalog, String conversationContext) {
        String basePrompt;
        if (systemPrompt == null || systemPrompt.trim().isEmpty()
                || !systemPrompt.contains("intent")) {
            basePrompt = DEFAULT_CHATBOT_PROMPT;
        } else {
            basePrompt = systemPrompt;
        }

        String memory = buildConversationMemoryPrompt(conversationContext);
        String catalog = buildCatalogPrompt(riceCatalog);
        if (memory.isEmpty() && catalog.isEmpty()) {
            return basePrompt;
        }
        StringBuilder prompt = new StringBuilder(basePrompt);
        if (!memory.isEmpty()) {
            prompt.append("\n\n").append(memory);
        }
        if (!catalog.isEmpty()) {
            prompt.append("\n\nRice catalog:\n").append(catalog);
        }
        return prompt.toString();
    }

    private String buildConversationMemoryPrompt(String conversationContext) {
        if (conversationContext == null || conversationContext.trim().isEmpty()) {
            return "";
        }
        return """
                Recent Messenger conversation memory:
                %s

                Use this memory to answer follow-up questions about what the customer already asked, compared, chose, or ordered.
                If the customer asks what they mentioned earlier, answer from this memory.
                For new orders, do not reuse address, phone, rice type, or quantity from an already completed order unless the customer explicitly says to use the same information.
                """
                .formatted(conversationContext.trim());
    }

    private String buildCatalogPrompt(List<RiceProduct> riceCatalog) {
        if (riceCatalog == null || riceCatalog.isEmpty()) {
            return "";
        }
        return riceCatalog.stream()
                .map(product -> "- " + product.getName()
                        + ": price_per_kg=" + product.getPricePerKg()
                        + ", profit_per_kg=" + product.getProfitPerKg()
                        + ", characteristics=" + product.getCharacteristics())
                .collect(Collectors.joining("\n"));
    }

    private AiParsedOrder toParsedOrder(AiChatbotResult result) {
        AiParsedOrder parsed = new AiParsedOrder();
        parsed.setRiceType(result.getRiceType());
        parsed.setQuantity(result.getQuantity());
        parsed.setAddress(result.getAddress());
        return parsed;
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content.trim();
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
