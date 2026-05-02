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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
    private static final long ORDER_DRAFT_TTL_MS = 30 * 60 * 1000;
    private static final long CONVERSATION_MEMORY_TTL_MS = 60 * 60 * 1000;
    private static final int CONVERSATION_MEMORY_MAX_TURNS = 12;

    private final String verifyToken;
    private final AiParsingService aiParsingService;
    private final OrderRepository orderRepository;
    private final MessengerGraphApiService messengerGraphApiService;
    private final RiceProductService riceProductService;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> handledMessageIds = new ConcurrentHashMap<>();
    private final Map<String, PendingMessengerOrder> pendingOrderDrafts = new ConcurrentHashMap<>();
    private final Map<String, MessengerConversationMemory> conversationMemories = new ConcurrentHashMap<>();

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

        String conversationContext = conversationContextFor(senderId);
        long aiStartNs = System.nanoTime();
        Optional<AiChatbotResult> resultOpt = aiParsingService.chat(text, activeProducts, conversationContext);
        log.info("Messenger timing ai_total durationMs={} success={}",
                elapsedMs(aiStartNs),
                resultOpt.isPresent());

        if (resultOpt.isEmpty()) {
            String reply = fallbackReply();
            long replyStartNs = System.nanoTime();
            sendTextMessageAndRemember(senderId, text, reply);
            log.info("Messenger timing fallback_reply_total durationMs={}", elapsedMs(replyStartNs));
            log.info("Messenger timing message_total durationMs={} outcome=fallback", elapsedMs(messageStartNs));
            return;
        }

        AiChatbotResult result = resultOpt.get();
        PendingMessengerOrder existingDraft = getActiveOrderDraft(senderId);
        if (!result.isOrderIntent() && existingDraft == null) {
            String reply = result.replyOrDefault(fallbackReply());
            long replyStartNs = System.nanoTime();
            sendTextMessageAndRemember(senderId, text, reply);
            log.info("Messenger timing reply_total durationMs={}", elapsedMs(replyStartNs));
            log.info("Messenger timing message_total durationMs={} outcome=general_chat", elapsedMs(messageStartNs));
            return;
        }

        if (!result.isOrderIntent() && existingDraft != null && !hasAnyOrderField(result)) {
            String reply = result.replyOrDefault(fallbackReply());
            long replyStartNs = System.nanoTime();
            sendTextMessageAndRemember(senderId, text, reply);
            log.info("Messenger timing reply_total durationMs={}", elapsedMs(replyStartNs));
            log.info("Messenger timing message_total durationMs={} outcome=general_chat_with_pending_order",
                    elapsedMs(messageStartNs));
            return;
        }

        PendingMessengerOrder draft = existingDraft != null ? existingDraft : new PendingMessengerOrder();
        draft.merge(result, text);
        pendingOrderDrafts.put(senderId, draft);

        if (!draft.isComplete()) {
            String reply = buildMissingInfoMessage(draft);
            long replyStartNs = System.nanoTime();
            sendTextMessageAndRemember(senderId, text, reply);
            log.info("Messenger timing reply_total durationMs={}", elapsedMs(replyStartNs));
            log.info("Messenger timing message_total durationMs={} outcome=incomplete_order", elapsedMs(messageStartNs));
            return;
        }

        AiParsedOrder parsed = draft.toParsedOrder();
        Order order = new Order();
        order.setCustomerName("Messenger:" + senderId);
        order.setCustomerPhone(parsed.getCustomerPhone());
        order.setAddress(parsed.getAddress());
        order.setProductDetails(buildProductDetailsJson(parsed, draft.rawText()));
        order.setTotalPrice(BigDecimal.ZERO);
        order.setSource(OrderSource.MESSENGER);
        order.setStatus(OrderStatus.PENDING);

        long saveStartNs = System.nanoTime();
        orderRepository.save(order);
        log.info("Messenger timing order_save durationMs={}", elapsedMs(saveStartNs));
        pendingOrderDrafts.remove(senderId);

        String reply = buildConfirmationMessage(parsed);
        long replyStartNs = System.nanoTime();
        sendTextMessageAndRemember(senderId, text, reply);
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
                "Minh da ghi nhan don %s, so luong %s, giao den %s. SDT lien he: %s. Cua hang se xu ly som nhe.",
                parsed.getRiceType(),
                parsed.getQuantity(),
                parsed.getAddress(),
                parsed.getCustomerPhone());
    }

    private String fallbackReply() {
        return "Hien tai neu he thong chap chon hoac gap van de, ban co the lien he SDT 0342504323 de dat hang nhe.";
    }

    private String conversationContextFor(String senderId) {
        long now = System.currentTimeMillis();
        pruneConversationMemories(now);
        MessengerConversationMemory memory = conversationMemories.get(senderId);
        if (memory == null) {
            return "";
        }
        if (memory.isExpired(now)) {
            conversationMemories.remove(senderId);
            return "";
        }
        return memory.toPromptContext();
    }

    private void sendTextMessageAndRemember(String senderId, String userText, String replyText) {
        messengerGraphApiService.sendTextMessage(senderId, replyText);
        rememberConversationTurn(senderId, userText, replyText);
    }

    private void rememberConversationTurn(String senderId, String userText, String replyText) {
        long now = System.currentTimeMillis();
        pruneConversationMemories(now);
        conversationMemories
                .computeIfAbsent(senderId, key -> new MessengerConversationMemory())
                .add(userText, replyText);
    }

    private void pruneConversationMemories(long now) {
        conversationMemories.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private PendingMessengerOrder getActiveOrderDraft(String senderId) {
        long now = System.currentTimeMillis();
        prunePendingOrderDrafts(now);
        PendingMessengerOrder draft = pendingOrderDrafts.get(senderId);
        if (draft != null && draft.isExpired(now)) {
            pendingOrderDrafts.remove(senderId);
            return null;
        }
        return draft;
    }

    private boolean hasAnyOrderField(AiChatbotResult result) {
        return isNotBlank(result.getRiceType())
                || isNotBlank(result.getQuantity())
                || isNotBlank(result.getAddress())
                || isNotBlank(result.getCustomerPhone());
    }

    private String buildMissingInfoMessage(PendingMessengerOrder draft) {
        List<String> missingFields = new ArrayList<>();
        if (!isNotBlank(draft.riceType)) {
            missingFields.add("loai gao");
        }
        if (!isNotBlank(draft.quantity)) {
            missingFields.add("so luong");
        }
        if (!isNotBlank(draft.address)) {
            missingFields.add("dia chi giao hang");
        }
        if (!isNotBlank(draft.customerPhone)) {
            missingFields.add("so dien thoai");
        }
        return "Minh da ghi nhan thong tin hien co. Ban cho minh xin them "
                + joinVietnamese(missingFields)
                + " nhe.";
    }

    private String joinVietnamese(List<String> values) {
        if (values.isEmpty()) {
            return "thong tin don hang";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " va " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1))
                + " va "
                + values.get(values.size() - 1);
    }

    private void prunePendingOrderDrafts(long now) {
        pendingOrderDrafts.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
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

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class PendingMessengerOrder {
        private String riceType;
        private String quantity;
        private String address;
        private String customerPhone;
        private long updatedAtMs = System.currentTimeMillis();
        private final List<String> rawMessages = new ArrayList<>();

        private void merge(AiChatbotResult result, String rawText) {
            riceType = chooseNewValue(riceType, result.getRiceType());
            quantity = chooseNewValue(quantity, result.getQuantity());
            address = chooseNewValue(address, result.getAddress());
            customerPhone = chooseNewValue(customerPhone, result.getCustomerPhone());
            if (rawText != null && !rawText.trim().isEmpty()) {
                rawMessages.add(rawText.trim());
            }
            updatedAtMs = System.currentTimeMillis();
        }

        private boolean isComplete() {
            return isNotBlank(riceType)
                    && isNotBlank(quantity)
                    && isNotBlank(address)
                    && isNotBlank(customerPhone);
        }

        private boolean isExpired(long now) {
            return now - updatedAtMs > ORDER_DRAFT_TTL_MS;
        }

        private AiParsedOrder toParsedOrder() {
            AiParsedOrder parsed = new AiParsedOrder();
            parsed.setRiceType(riceType);
            parsed.setQuantity(quantity);
            parsed.setAddress(address);
            parsed.setCustomerPhone(customerPhone);
            return parsed;
        }

        private String rawText() {
            return String.join(" | ", rawMessages);
        }

        private static String chooseNewValue(String currentValue, String newValue) {
            if (isNotBlank(newValue)) {
                return newValue.trim();
            }
            return currentValue;
        }

        private static boolean isNotBlank(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }

    private static class MessengerConversationMemory {
        private final Deque<ConversationTurn> turns = new ArrayDeque<>();
        private long updatedAtMs = System.currentTimeMillis();

        private synchronized void add(String userText, String replyText) {
            if (!isNotBlank(userText) && !isNotBlank(replyText)) {
                return;
            }
            turns.addLast(new ConversationTurn(clean(userText), clean(replyText)));
            while (turns.size() > CONVERSATION_MEMORY_MAX_TURNS) {
                turns.removeFirst();
            }
            updatedAtMs = System.currentTimeMillis();
        }

        private synchronized String toPromptContext() {
            if (turns.isEmpty()) {
                return "";
            }
            StringBuilder context = new StringBuilder();
            int index = 1;
            for (ConversationTurn turn : turns) {
                context.append(index)
                        .append(". Customer: ")
                        .append(turn.userText)
                        .append("\n")
                        .append(index)
                        .append(". Assistant: ")
                        .append(turn.replyText)
                        .append("\n");
                index++;
            }
            return context.toString().trim();
        }

        private boolean isExpired(long now) {
            return now - updatedAtMs > CONVERSATION_MEMORY_TTL_MS;
        }

        private static String clean(String value) {
            if (value == null) {
                return "";
            }
            String normalized = value.replaceAll("\\s+", " ").trim();
            if (normalized.length() <= 500) {
                return normalized;
            }
            return normalized.substring(0, 500);
        }

        private static boolean isNotBlank(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }

    private static class ConversationTurn {
        private final String userText;
        private final String replyText;

        private ConversationTurn(String userText, String replyText) {
            this.userText = userText;
            this.replyText = replyText;
        }
    }
}
