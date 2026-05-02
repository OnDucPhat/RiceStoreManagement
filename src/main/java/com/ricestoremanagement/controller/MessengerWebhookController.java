package com.ricestoremanagement.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ricestoremanagement.dto.chat.ChatMessageResponse;
import com.ricestoremanagement.dto.messenger.WebhookEntry;
import com.ricestoremanagement.dto.messenger.WebhookMessaging;
import com.ricestoremanagement.dto.messenger.WebhookPayload;
import com.ricestoremanagement.model.enums.OrderSource;
import com.ricestoremanagement.service.ChatbotOrderFlowService;
import com.ricestoremanagement.service.MessengerGraphApiService;

@RestController
@RequestMapping("/webhook")
public class MessengerWebhookController {
    private static final Logger log = LoggerFactory.getLogger(MessengerWebhookController.class);

    private final String verifyToken;
    private final ChatbotOrderFlowService chatbotOrderFlowService;
    private final MessengerGraphApiService messengerGraphApiService;

    public MessengerWebhookController(
            @Value("${meta.webhook.verify-token:}") String verifyToken,
            ChatbotOrderFlowService chatbotOrderFlowService,
            MessengerGraphApiService messengerGraphApiService) {
        this.verifyToken = verifyToken;
        this.chatbotOrderFlowService = chatbotOrderFlowService;
        this.messengerGraphApiService = messengerGraphApiService;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
    }

    @PostMapping
    public ResponseEntity<String> receiveMessage(@RequestBody WebhookPayload payload) {
        long webhookStartNs = System.nanoTime();
        if (payload == null || payload.getEntry() == null) {
            log.info("Messenger timing webhook_total durationMs=0 messages=0");
            return ResponseEntity.ok("EVENT_RECEIVED");
        }

        int handledMessages = 0;
        for (WebhookEntry entry : payload.getEntry()) {
            if (entry.getMessaging() == null) {
                continue;
            }
            for (WebhookMessaging messaging : entry.getMessaging()) {
                handleIncomingMessage(messaging);
                handledMessages++;
            }
        }

        log.info("Messenger timing webhook_total durationMs={} messages={}",
                elapsedMs(webhookStartNs),
                handledMessages);
        return ResponseEntity.ok("EVENT_RECEIVED");
    }

    private void handleIncomingMessage(WebhookMessaging messaging) {
        long messageStartNs = System.nanoTime();
        if (messaging == null || messaging.getMessage() == null) {
            return;
        }

        String senderId = messaging.getSender() != null ? messaging.getSender().getId() : null;
        String text = messaging.getMessage().getText();
        if (senderId == null || senderId.trim().isEmpty() || text == null || text.trim().isEmpty()) {
            return;
        }

        ChatMessageResponse response = chatbotOrderFlowService.handleMessage(
                "messenger",
                senderId,
                "Messenger:" + senderId,
                OrderSource.MESSENGER,
                text);

        long replyStartNs = System.nanoTime();
        messengerGraphApiService.sendTextMessage(senderId, response.getReply());
        log.info("Messenger timing reply_total durationMs={}", elapsedMs(replyStartNs));
        log.info("Messenger timing message_total durationMs={} outcome={}",
                elapsedMs(messageStartNs),
                response.getOutcome());
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }
}
