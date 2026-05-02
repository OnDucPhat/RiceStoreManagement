package com.ricestoremanagement.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricestoremanagement.dto.ai.AiChatbotResult;
import com.ricestoremanagement.dto.ai.AiParsedOrder;
import com.ricestoremanagement.dto.messenger.WebhookEntry;
import com.ricestoremanagement.dto.messenger.WebhookMessaging;
import com.ricestoremanagement.dto.messenger.WebhookPayload;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.RiceProduct;
import com.ricestoremanagement.model.enums.OrderSource;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.repository.OrderRepository;
import com.ricestoremanagement.service.AiParsingService;
import com.ricestoremanagement.service.MessengerGraphApiService;
import com.ricestoremanagement.service.RiceProductService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

@RestController
@RequestMapping("/webhook")
public class MessengerWebhookController {
    private static final Logger log = LoggerFactory.getLogger(MessengerWebhookController.class);
    private static final long MESSAGE_DEDUP_TTL_MS = 10 * 60 * 1000;

    private final String verifyToken;
    private final AiParsingService aiParsingService;
    private final OrderRepository orderRepository;
    private final MessengerGraphApiService messengerGraphApiService;
    private final RiceProductService riceProductService;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> handledMessageIds = new ConcurrentHashMap<>();

    public MessengerWebhookController(
            @Value("${meta.webhook.verify-token:}") String verifyToken,
            AiParsingService aiParsingService,
            OrderRepository orderRepository,
            MessengerGraphApiService messengerGraphApiService,
            RiceProductService riceProductService,
            ObjectMapper objectMapper) {
        this.verifyToken = verifyToken;
        this.aiParsingService = aiParsingService;
        this.orderRepository = orderRepository;
        this.messengerGraphApiService = messengerGraphApiService;
        this.riceProductService = riceProductService;
        this.objectMapper = objectMapper;
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

        if (isDuplicateMessage(messaging)) {
            log.info("Messenger timing message_total durationMs={} outcome=duplicate_skipped", elapsedMs(messageStartNs));
            return;
        }

        long catalogStartNs = System.nanoTime();
        List<RiceProduct> activeProducts = riceProductService.getProducts(true);
        log.info("Messenger timing catalog_query durationMs={} products={}",
                elapsedMs(catalogStartNs),
                activeProducts.size());

        long aiStartNs = System.nanoTime();
        Optional<AiChatbotResult> resultOpt = aiParsingService.chat(text, activeProducts);
        log.info("Messenger timing ai_total durationMs={} success={}",
                elapsedMs(aiStartNs),
                resultOpt.isPresent());

        if (resultOpt.isEmpty()) {
            long replyStartNs = System.nanoTime();
            messengerGraphApiService.sendTextMessage(senderId, fallbackReply());
            log.info("Messenger timing fallback_reply_total durationMs={}", elapsedMs(replyStartNs));
            log.info("Messenger timing message_total durationMs={} outcome=fallback", elapsedMs(messageStartNs));
            return;
        }

        AiChatbotResult result = resultOpt.get();
        if (!result.isOrderIntent()) {
            long replyStartNs = System.nanoTime();
            messengerGraphApiService.sendTextMessage(
                    senderId,
                    result.replyOrDefault(fallbackReply()));
            log.info("Messenger timing reply_total durationMs={}", elapsedMs(replyStartNs));
            log.info("Messenger timing message_total durationMs={} outcome=general_chat", elapsedMs(messageStartNs));
            return;
        }

        if (!result.isCompleteOrder()) {
            long replyStartNs = System.nanoTime();
            messengerGraphApiService.sendTextMessage(
                    senderId,
                    result.replyOrDefault("Ban cho minh xin them loai gao, so luong va dia chi giao hang nhe."));
            log.info("Messenger timing reply_total durationMs={}", elapsedMs(replyStartNs));
            log.info("Messenger timing message_total durationMs={} outcome=incomplete_order", elapsedMs(messageStartNs));
            return;
        }

        AiParsedOrder parsed = toParsedOrder(result);
        Order order = new Order();
        order.setCustomerName("Messenger:" + senderId);
        order.setAddress(parsed.getAddress());
        order.setProductDetails(buildProductDetailsJson(parsed, text));
        order.setTotalPrice(BigDecimal.ZERO);
        order.setSource(OrderSource.MESSENGER);
        order.setStatus(OrderStatus.PENDING);

        long saveStartNs = System.nanoTime();
        orderRepository.save(order);
        log.info("Messenger timing order_save durationMs={}", elapsedMs(saveStartNs));

        long replyStartNs = System.nanoTime();
        messengerGraphApiService.sendTextMessage(
                senderId,
                result.replyOrDefault(buildConfirmationMessage(parsed)));
        log.info("Messenger timing reply_total durationMs={}", elapsedMs(replyStartNs));

        log.info("Created Messenger order from sender {}", senderId);
        log.info("Messenger timing message_total durationMs={} outcome=order_created", elapsedMs(messageStartNs));
    }

    private String buildProductDetailsJson(AiParsedOrder parsed, String rawText) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rice_type", parsed.getRiceType());
        details.put("quantity", parsed.getQuantity());
        details.put("address", parsed.getAddress());
        details.put("customer_phone", parsed.getCustomerPhone());
        details.put("raw_message", rawText);

        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize product details: {}", ex.getMessage());
            return rawText;
        }
    }

    private String buildConfirmationMessage(AiParsedOrder parsed) {
        return String.format(
                "Minh da ghi nhan don %s, so luong %s, giao den %s. Cua hang se xu ly som nhe.",
                parsed.getRiceType(),
                parsed.getQuantity(),
                parsed.getAddress());
    }

    private AiParsedOrder toParsedOrder(AiChatbotResult result) {
        AiParsedOrder parsed = new AiParsedOrder();
        parsed.setRiceType(result.getRiceType());
        parsed.setQuantity(result.getQuantity());
        parsed.setAddress(result.getAddress());
        parsed.setCustomerPhone(result.getCustomerPhone());
        return parsed;
    }

    private String fallbackReply() {
        return "Hien tai neu he thong chap chon hoac gap van de, ban co the lien he SDT 0342504323 de dat hang nhe.";
    }

    private boolean isDuplicateMessage(WebhookMessaging messaging) {
        String messageId = messaging.getMessage().getMid();
        if (messageId == null || messageId.trim().isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        pruneHandledMessageIds(now);
        Long existing = handledMessageIds.putIfAbsent(messageId, now);
        if (existing != null) {
            log.info("Skipping duplicate Messenger message id={}", messageId);
            return true;
        }
        return false;
    }

    private void pruneHandledMessageIds(long now) {
        handledMessageIds.entrySet().removeIf(entry -> now - entry.getValue() > MESSAGE_DEDUP_TTL_MS);
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }
}
