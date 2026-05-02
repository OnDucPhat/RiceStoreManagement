package com.ricestoremanagement.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricestoremanagement.dto.ai.AiChatbotResult;
import com.ricestoremanagement.dto.ai.AiParsedOrder;
import com.ricestoremanagement.dto.chat.ChatMessageResponse;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.RiceProduct;
import com.ricestoremanagement.model.enums.OrderSource;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.repository.OrderRepository;

@Service
public class ChatbotOrderFlowService {
    private static final Logger log = LoggerFactory.getLogger(ChatbotOrderFlowService.class);
    private static final long ORDER_DRAFT_TTL_MS = 30 * 60 * 1000;
    private static final long CONVERSATION_MEMORY_TTL_MS = 60 * 60 * 1000;
    private static final int CONVERSATION_MEMORY_MAX_TURNS = 12;

    private final AiParsingService aiParsingService;
    private final OrderRepository orderRepository;
    private final RiceProductService riceProductService;
    private final ObjectMapper objectMapper;
    private final Map<String, PendingChatOrder> pendingOrderDrafts = new ConcurrentHashMap<>();
    private final Map<String, ChatConversationMemory> conversationMemories = new ConcurrentHashMap<>();

    public ChatbotOrderFlowService(
            AiParsingService aiParsingService,
            OrderRepository orderRepository,
            RiceProductService riceProductService,
            ObjectMapper objectMapper) {
        this.aiParsingService = aiParsingService;
        this.orderRepository = orderRepository;
        this.riceProductService = riceProductService;
        this.objectMapper = objectMapper;
    }

    public ChatMessageResponse handleMessage(
            String channel,
            String conversationId,
            String customerName,
            OrderSource orderSource,
            String text) {
        long messageStartNs = System.nanoTime();
        String safeConversationId = cleanConversationId(conversationId);
        String conversationKey = channel + ":" + safeConversationId;
        String normalizedText = normalizeVietnameseText(text);

        long catalogStartNs = System.nanoTime();
        List<RiceProduct> activeProducts = riceProductService.getProducts(true);
        log.info("Chatbot timing catalog_query durationMs={} products={}",
                elapsedMs(catalogStartNs),
                activeProducts.size());

        PendingChatOrder existingDraft = getActiveOrderDraft(conversationKey);
        if (existingDraft != null && existingDraft.isAwaitingConfirmation()) {
            if (isConfirmationMessage(normalizedText)) {
                Order order = saveOrder(customerName, orderSource, existingDraft);
                pendingOrderDrafts.remove(conversationKey);

                String reply = buildConfirmationMessage(existingDraft, order);
                log.info("Chatbot timing message_total durationMs={} outcome=order_created",
                        elapsedMs(messageStartNs));
                return replyAndRemember(safeConversationId, conversationKey, text, reply,
                        order.getId(), true, "order_created");
            }

            if (isNoMoreItemsMessage(normalizedText)) {
                existingDraft.markAwaitingConfirmKeyword();
                String reply = buildConfirmInstructionMessage(existingDraft);
                log.info("Chatbot timing message_total durationMs={} outcome=awaiting_confirm_keyword",
                        elapsedMs(messageStartNs));
                return replyAndRemember(safeConversationId, conversationKey, text, reply,
                        null, false, "awaiting_confirm_keyword");
            }

            if (isAddMoreAffirmationOnly(normalizedText)) {
                existingDraft.prepareForAdditionalItem();
                String reply = buildAdditionalItemDetailsRequest(existingDraft);
                log.info("Chatbot timing message_total durationMs={} outcome=awaiting_additional_item_details",
                        elapsedMs(messageStartNs));
                return replyAndRemember(safeConversationId, conversationKey, text, reply,
                        null, false, "awaiting_additional_item_details");
            }
        }

        String conversationContext = conversationContextFor(conversationKey);
        long aiStartNs = System.nanoTime();
        Optional<AiChatbotResult> resultOpt = aiParsingService.chat(text, activeProducts, conversationContext);
        log.info("Chatbot timing ai_total durationMs={} success={}",
                elapsedMs(aiStartNs),
                resultOpt.isPresent());

        if (resultOpt.isEmpty()) {
            String reply = fallbackReply();
            log.info("Chatbot timing message_total durationMs={} outcome=fallback", elapsedMs(messageStartNs));
            return replyAndRemember(safeConversationId, conversationKey, text, reply,
                    null, false, "fallback");
        }

        AiChatbotResult result = resultOpt.get();
        if (!result.isOrderIntent() && existingDraft == null) {
            String reply = result.replyOrDefault(fallbackReply());
            log.info("Chatbot timing message_total durationMs={} outcome=general_chat",
                    elapsedMs(messageStartNs));
            return replyAndRemember(safeConversationId, conversationKey, text, reply,
                    null, false, "general_chat");
        }

        if (!result.isOrderIntent() && existingDraft != null && !hasAnyOrderField(result)) {
            String reply = existingDraft.isAwaitingConfirmation()
                    ? buildPendingOrderReminder(existingDraft)
                    : result.replyOrDefault(fallbackReply());
            log.info("Chatbot timing message_total durationMs={} outcome=general_chat_with_pending_order",
                    elapsedMs(messageStartNs));
            return replyAndRemember(safeConversationId, conversationKey, text, reply,
                    null, false, "general_chat_with_pending_order");
        }

        PendingChatOrder draft = existingDraft != null ? existingDraft : new PendingChatOrder();
        boolean hasOrderItemField = hasAnyOrderItemField(result);
        if (draft.isAwaitingConfirmation() && hasOrderItemField) {
            draft.prepareForAdditionalItem();
        }
        draft.merge(result, text);
        pendingOrderDrafts.put(conversationKey, draft);

        if (draft.isAwaitingConfirmation() && !hasOrderItemField) {
            String reply = buildPendingOrderReminder(draft);
            log.info("Chatbot timing message_total durationMs={} outcome=updated_pending_order",
                    elapsedMs(messageStartNs));
            return replyAndRemember(safeConversationId, conversationKey, text, reply,
                    null, false, "updated_pending_order");
        }

        if (!draft.isComplete()) {
            String reply = buildMissingInfoMessage(draft);
            log.info("Chatbot timing message_total durationMs={} outcome=incomplete_order",
                    elapsedMs(messageStartNs));
            return replyAndRemember(safeConversationId, conversationKey, text, reply,
                    null, false, "incomplete_order");
        }

        draft.markAwaitingMoreItems();
        String reply = buildMoreItemsQuestionMessage(draft);
        log.info("Chatbot timing message_total durationMs={} outcome=awaiting_more_items",
                elapsedMs(messageStartNs));
        return replyAndRemember(safeConversationId, conversationKey, text, reply,
                null, false, "awaiting_more_items");
    }

    private ChatMessageResponse replyAndRemember(
            String sessionId,
            String conversationKey,
            String userText,
            String replyText,
            Long orderId,
            boolean orderCreated,
            String outcome) {
        rememberConversationTurn(conversationKey, userText, replyText);
        return new ChatMessageResponse(sessionId, replyText, orderId, orderCreated, outcome);
    }

    private Order saveOrder(String customerName, OrderSource orderSource, PendingChatOrder draft) {
        AiParsedOrder parsed = draft.toParsedOrder();
        Order order = new Order();
        order.setCustomerName(customerName);
        order.setCustomerPhone(parsed.getCustomerPhone());
        order.setAddress(parsed.getAddress());
        order.setProductDetails(buildProductDetailsJson(draft));
        order.setTotalPrice(BigDecimal.ZERO);
        order.setSource(orderSource);
        order.setStatus(OrderStatus.PENDING);

        long saveStartNs = System.nanoTime();
        Order savedOrder = orderRepository.save(order);
        log.info("Chatbot timing order_save durationMs={}", elapsedMs(saveStartNs));
        return savedOrder;
    }

    private String buildProductDetailsJson(PendingChatOrder draft) {
        Map<String, Object> details = new LinkedHashMap<>();
        AiParsedOrder parsed = draft.toParsedOrder();
        details.put("rice_type", parsed.getRiceType());
        details.put("quantity", parsed.getQuantity());
        details.put("address", parsed.getAddress());
        details.put("customer_phone", parsed.getCustomerPhone());
        details.put("items", draft.orderItemsForJson());
        details.put("raw_message", draft.rawText());

        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize product details: {}", ex.getMessage());
            return draft.rawText();
        }
    }

    private String buildConfirmationMessage(PendingChatOrder draft, Order order) {
        AiParsedOrder parsed = draft.toParsedOrder();
        return String.format(
                "Mình đã chốt đơn #%s gồm %s, giao đến %s. SĐT liên hệ: %s. Cửa hàng sẽ sớm xử lí cho bạn nhé.",
                order.getId(),
                draft.summaryForMessage(),
                parsed.getAddress(),
                parsed.getCustomerPhone());
    }

    private String buildMoreItemsQuestionMessage(PendingChatOrder draft) {
        AiParsedOrder parsed = draft.toParsedOrder();
        return String.format(
                "Mình đã ghi nhận đơn gồm %s, giao đến %s. SĐT liên hệ: %s. Bạn có muốn đặt thêm gì nữa không?",
                draft.summaryForMessage(),
                parsed.getAddress(),
                parsed.getCustomerPhone());
    }

    private String buildConfirmInstructionMessage(PendingChatOrder draft) {
        AiParsedOrder parsed = draft.toParsedOrder();
        return String.format(
                "Dạ được, đơn hiện tại gồm %s, giao đến %s. SĐT liên hệ: %s. Bạn nhắn \"xác nhận\" để mình chốt đơn và gửi về hệ thống nhé.",
                draft.summaryForMessage(),
                parsed.getAddress(),
                parsed.getCustomerPhone());
    }

    private String buildAdditionalItemDetailsRequest(PendingChatOrder draft) {
        StringBuilder reply = new StringBuilder("Dạ được, bạn muốn đặt thêm loại gạo nào và số lượng bao nhiêu?");
        if (isNotBlank(draft.address) && isNotBlank(draft.customerPhone)) {
            reply.append(" Mình sẽ dùng địa chỉ và SĐT bạn đã gửi nếu bạn không đổi.");
        }
        return reply.toString();
    }

    private String buildPendingOrderReminder(PendingChatOrder draft) {
        if (draft.isAwaitingConfirmKeyword()) {
            return buildConfirmInstructionMessage(draft);
        }
        return buildMoreItemsQuestionMessage(draft)
                + " Nếu không, bạn nhắn \"không\" rồi nhắn \"xác nhận\" để mình chốt đơn nhé.";
    }

    private String fallbackReply() {
        return "hiện tại hệ thống chập chờn hoặc đang gặp vấn đề, bạn có thể liên hệ sđt 0342504323 để được xử lí nhé";
    }

    private String conversationContextFor(String conversationKey) {
        long now = System.currentTimeMillis();
        pruneConversationMemories(now);
        ChatConversationMemory memory = conversationMemories.get(conversationKey);
        if (memory == null) {
            return "";
        }
        if (memory.isExpired(now)) {
            conversationMemories.remove(conversationKey);
            return "";
        }
        return memory.toPromptContext();
    }

    private void rememberConversationTurn(String conversationKey, String userText, String replyText) {
        long now = System.currentTimeMillis();
        pruneConversationMemories(now);
        conversationMemories
                .computeIfAbsent(conversationKey, key -> new ChatConversationMemory())
                .add(userText, replyText);
    }

    private void pruneConversationMemories(long now) {
        conversationMemories.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private PendingChatOrder getActiveOrderDraft(String conversationKey) {
        long now = System.currentTimeMillis();
        prunePendingOrderDrafts(now);
        PendingChatOrder draft = pendingOrderDrafts.get(conversationKey);
        if (draft != null && draft.isExpired(now)) {
            pendingOrderDrafts.remove(conversationKey);
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

    private boolean hasAnyOrderItemField(AiChatbotResult result) {
        return isNotBlank(result.getRiceType()) || isNotBlank(result.getQuantity());
    }

    private String buildMissingInfoMessage(PendingChatOrder draft) {
        List<String> missingFields = new ArrayList<>();
        if (!isNotBlank(draft.riceType)) {
            missingFields.add("loại gạo");
        }
        if (!isNotBlank(draft.quantity)) {
            missingFields.add("số lượng");
        }
        if (!isNotBlank(draft.address)) {
            missingFields.add("địa chỉ giao hàng");
        }
        if (!isNotBlank(draft.customerPhone)) {
            missingFields.add("số điện thoại");
        }
        return "Mình đã ghi nhận thông tin hiện có. Bạn cho mình xin thêm "
                + joinVietnamese(missingFields)
                + " nhé.";
    }

    private String joinVietnamese(List<String> values) {
        if (values.isEmpty()) {
            return "thông tin đơn hàng";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " và " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1))
                + " và "
                + values.get(values.size() - 1);
    }

    private void prunePendingOrderDrafts(long now) {
        pendingOrderDrafts.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }

    private boolean isConfirmationMessage(String normalizedText) {
        return "xac nhan".equals(normalizedText)
                || "confirm".equals(normalizedText)
                || "ok xac nhan".equals(normalizedText)
                || "toi xac nhan".equals(normalizedText)
                || "minh xac nhan".equals(normalizedText);
    }

    private boolean isNoMoreItemsMessage(String normalizedText) {
        return "khong".equals(normalizedText)
                || "ko".equals(normalizedText)
                || "k".equals(normalizedText)
                || "khong them".equals(normalizedText)
                || "ko them".equals(normalizedText)
                || "k them".equals(normalizedText)
                || "khong can them".equals(normalizedText)
                || "khong mua them".equals(normalizedText)
                || "vay thoi".equals(normalizedText)
                || "du roi".equals(normalizedText)
                || "het roi".equals(normalizedText);
    }

    private boolean isAddMoreAffirmationOnly(String normalizedText) {
        return "co".equals(normalizedText)
                || "co them".equals(normalizedText)
                || "muon them".equals(normalizedText)
                || "dat them".equals(normalizedText)
                || "them".equals(normalizedText);
    }

    private String normalizeVietnameseText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanConversationId(String value) {
        if (!isNotBlank(value)) {
            return "anonymous";
        }
        String cleaned = value.replaceAll("[^a-zA-Z0-9._:-]", "").trim();
        if (cleaned.isEmpty()) {
            return "anonymous";
        }
        if (cleaned.length() <= 100) {
            return cleaned;
        }
        return cleaned.substring(0, 100);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class PendingChatOrder {
        private String riceType;
        private String quantity;
        private String address;
        private String customerPhone;
        private boolean awaitingMoreItems;
        private boolean awaitingConfirmKeyword;
        private boolean addingAdditionalItem;
        private long updatedAtMs = System.currentTimeMillis();
        private final List<PendingChatOrderItem> items = new ArrayList<>();
        private final List<String> rawMessages = new ArrayList<>();

        private void merge(AiChatbotResult result, String rawText) {
            riceType = chooseNewValue(riceType, result.getRiceType());
            quantity = chooseNewValue(quantity, result.getQuantity());
            address = chooseNewValue(address, result.getAddress());
            customerPhone = chooseNewValue(customerPhone, result.getCustomerPhone());
            if (isNotBlank(riceType) && isNotBlank(quantity)) {
                upsertCurrentItem();
            }
            if (rawText != null && !rawText.trim().isEmpty()) {
                rawMessages.add(rawText.trim());
            }
            awaitingConfirmKeyword = false;
            addingAdditionalItem = false;
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

        private boolean isAwaitingConfirmation() {
            return awaitingMoreItems || awaitingConfirmKeyword;
        }

        private boolean isAwaitingConfirmKeyword() {
            return awaitingConfirmKeyword;
        }

        private void markAwaitingMoreItems() {
            upsertCurrentItem();
            awaitingMoreItems = true;
            awaitingConfirmKeyword = false;
            addingAdditionalItem = false;
            updatedAtMs = System.currentTimeMillis();
        }

        private void markAwaitingConfirmKeyword() {
            awaitingMoreItems = false;
            awaitingConfirmKeyword = true;
            addingAdditionalItem = false;
            updatedAtMs = System.currentTimeMillis();
        }

        private void prepareForAdditionalItem() {
            upsertCurrentItem();
            riceType = null;
            quantity = null;
            awaitingMoreItems = false;
            awaitingConfirmKeyword = false;
            addingAdditionalItem = true;
            updatedAtMs = System.currentTimeMillis();
        }

        private AiParsedOrder toParsedOrder() {
            AiParsedOrder parsed = new AiParsedOrder();
            PendingChatOrderItem firstItem = firstItem();
            parsed.setRiceType(firstItem != null ? firstItem.riceType : riceType);
            parsed.setQuantity(firstItem != null ? firstItem.quantity : quantity);
            parsed.setAddress(address);
            parsed.setCustomerPhone(customerPhone);
            return parsed;
        }

        private String rawText() {
            return String.join(" | ", rawMessages);
        }

        private List<Map<String, String>> orderItemsForJson() {
            upsertCurrentItem();
            List<Map<String, String>> itemMaps = new ArrayList<>();
            for (PendingChatOrderItem item : items) {
                Map<String, String> itemMap = new LinkedHashMap<>();
                itemMap.put("rice_type", item.riceType);
                itemMap.put("quantity", item.quantity);
                itemMaps.add(itemMap);
            }
            return itemMaps;
        }

        private String summaryForMessage() {
            upsertCurrentItem();
            if (items.isEmpty()) {
                return riceType + ", số lượng " + quantity;
            }
            List<String> parts = new ArrayList<>();
            for (PendingChatOrderItem item : items) {
                parts.add(item.riceType + " - " + item.quantity);
            }
            return String.join("; ", parts);
        }

        private void upsertCurrentItem() {
            if (!isNotBlank(riceType) || !isNotBlank(quantity)) {
                return;
            }
            PendingChatOrderItem lastItem = items.isEmpty() ? null : items.get(items.size() - 1);
            if (lastItem != null && lastItem.matches(riceType, quantity)) {
                return;
            }
            if (lastItem != null && !awaitingMoreItems && !awaitingConfirmKeyword && !addingAdditionalItem) {
                lastItem.riceType = riceType.trim();
                lastItem.quantity = quantity.trim();
                return;
            }
            items.add(new PendingChatOrderItem(riceType.trim(), quantity.trim()));
        }

        private PendingChatOrderItem firstItem() {
            upsertCurrentItem();
            if (items.isEmpty()) {
                return null;
            }
            return items.get(0);
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

    private static class PendingChatOrderItem {
        private String riceType;
        private String quantity;

        private PendingChatOrderItem(String riceType, String quantity) {
            this.riceType = riceType;
            this.quantity = quantity;
        }

        private boolean matches(String riceType, String quantity) {
            return this.riceType.equalsIgnoreCase(riceType.trim())
                    && this.quantity.equalsIgnoreCase(quantity.trim());
        }
    }

    private static class ChatConversationMemory {
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
