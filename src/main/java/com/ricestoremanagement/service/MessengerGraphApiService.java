package com.ricestoremanagement.service;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MessengerGraphApiService {
    private static final Logger log = LoggerFactory.getLogger(MessengerGraphApiService.class);

    private final RestClient restClient;
    private final String pageAccessToken;

    public MessengerGraphApiService(
            @Value("${meta.graph.base-url:https://graph.facebook.com/v19.0}") String baseUrl,
            @Value("${meta.graph.page-access-token:}") String pageAccessToken) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.pageAccessToken = pageAccessToken;
    }

    public void sendTextMessage(String recipientId, String text) {
        if (recipientId == null || recipientId.trim().isEmpty() || text == null || text.trim().isEmpty()) {
            return;
        }
        if (pageAccessToken == null || pageAccessToken.trim().isEmpty()) {
            log.warn("Page access token not configured; skipping reply to Messenger user {}", recipientId);
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_type", "RESPONSE");
        payload.put("recipient", Map.of("id", recipientId));
        payload.put("message", Map.of("text", text));

        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/me/messages")
                            .queryParam("access_token", pageAccessToken)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Failed to send Messenger reply: {}", ex.getMessage());
        }
    }
}
