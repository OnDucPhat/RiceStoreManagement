package com.ricestoremanagement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern FIRST_NUMBER_PATTERN = Pattern.compile("(\\d+(?:[\\.,]\\d+)?)");
    private static final Pattern QUANTITY_TEXT_PATTERN = Pattern.compile(
            "\\b\\d+(?:[\\.,]\\d+)?\\s*(?:kg|kilogram|kilo|ky|ki|can|bao)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("0\\d{9,10}");

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
                Order order = saveOrder(customerName, orderSource, existingDraft, activeProducts);
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
            // Mark that we're asking for customer name if it's missing
            if (!isNotBlank(draft.customerName)) {
                draft.customerNameAsked = true;
                pendingOrderDrafts.put(conversationKey, draft);
            }
            String reply = buildMissingInfoMessage(draft);
            log.info("Chatbot timing message_total durationMs={} outcome=incomplete_order",
                    elapsedMs(messageStartNs));
            return replyAndRemember(safeConversationId, conversationKey, text, reply,
                    null, false, "incomplete_order");
        }

        // Order is complete – before asking for more items, check if loyalty phone is needed
        if (!draft.isLoyaltyPhoneCollected()) {
            if (!draft.loyaltyPhoneAsked) {
                // First time - ask for loyalty phone
                draft.loyaltyPhoneAsked = true;
                pendingOrderDrafts.put(conversationKey, draft);
                String reply = buildLoyaltyPhoneRequest(draft);
                log.info("Chatbot timing message_total durationMs={} outcome=awaiting_loyalty_phone",
                        elapsedMs(messageStartNs));
                return replyAndRemember(safeConversationId, conversationKey, text, reply,
                        null, false, "awaiting_loyalty_phone");
            } else {
                // Already asked - extract from current message
                String loyaltyPhoneGuess = extractPhone(normalizedText);
                if (loyaltyPhoneGuess != null && !loyaltyPhoneGuess.isBlank()) {
                    draft.setLoyaltyPhone(loyaltyPhoneGuess);
                } else if (isBopQua(normalizedText)) {
                    draft.setLoyaltyPhone("");
                }
                pendingOrderDrafts.put(conversationKey, draft);

                if (!draft.isLoyaltyPhoneCollected()) {
                    // Still not collected - ask again
                    String reply = buildLoyaltyPhoneRequest(draft);
                    log.info("Chatbot timing message_total durationMs={} outcome=awaiting_loyalty_phone_retry",
                            elapsedMs(messageStartNs));
                    return replyAndRemember(safeConversationId, conversationKey, text, reply,
                            null, false, "awaiting_loyalty_phone_retry");
                }
            }
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

    private Order saveOrder(
            String customerName,
            OrderSource orderSource,
            PendingChatOrder draft,
            List<RiceProduct> activeProducts) {
        AiParsedOrder parsed = draft.toParsedOrder();
        OrderPricing pricing = priceOrder(draft, activeProducts);
        Order order = new Order();
        // Use customerName from draft if available, otherwise use the provided customerName
        String finalCustomerName = (draft.customerName != null && !draft.customerName.trim().isEmpty())
                ? draft.customerName.trim()
                : customerName;
        order.setCustomerName(finalCustomerName);
        order.setCustomerPhone(parsed.getCustomerPhone());
        order.setAddress(parsed.getAddress());
        order.setProductDetails(buildProductDetailsJson(draft, pricing));
        order.setTotalPrice(pricing.totalPrice());
        order.setSource(orderSource);
        order.setStatus(OrderStatus.PENDING);

        long saveStartNs = System.nanoTime();
        Order savedOrder = orderRepository.save(order);
        log.info("Chatbot timing order_save durationMs={}", elapsedMs(saveStartNs));
        return savedOrder;
    }

    private OrderPricing priceOrder(PendingChatOrder draft, List<RiceProduct> activeProducts) {
        List<OrderLine> lines = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (PendingChatOrderItem item : draft.normalizedItemsForOrder()) {
            String riceType = cleanRiceTypeText(item.riceType);
            BigDecimal quantityKg = parseQuantityKg(item.quantity);
            RiceProduct product = findBestProduct(riceType, activeProducts).orElse(null);
            BigDecimal lineTotal = null;
            if (product != null && quantityKg != null) {
                lineTotal = product.getPricePerKg()
                        .multiply(quantityKg)
                        .setScale(2, RoundingMode.HALF_UP);
                totalPrice = totalPrice.add(lineTotal);
            }
            lines.add(new OrderLine(riceType, item.quantity, quantityKg, product, lineTotal));
        }

        return new OrderPricing(lines, totalPrice.setScale(2, RoundingMode.HALF_UP));
    }

    private Optional<RiceProduct> findBestProduct(String riceType, List<RiceProduct> activeProducts) {
        String lookup = normalizeProductLookupText(riceType);
        if (lookup.isEmpty() || activeProducts == null || activeProducts.isEmpty()) {
            return Optional.empty();
        }

        RiceProduct bestProduct = null;
        int bestScore = 0;
        for (RiceProduct product : activeProducts) {
            if (product == null || !product.isActive()) {
                continue;
            }
            String productName = normalizeProductLookupText(product.getName());
            int score = productMatchScore(lookup, productName);
            if (score > bestScore) {
                bestProduct = product;
                bestScore = score;
            }
        }
        return Optional.ofNullable(bestProduct);
    }

    private int productMatchScore(String lookup, String productName) {
        if (lookup.isEmpty() || productName.isEmpty()) {
            return 0;
        }
        if (lookup.equals(productName)) {
            return 10_000;
        }
        String lookupWithoutPrefix = removeRicePrefix(lookup);
        String productWithoutPrefix = removeRicePrefix(productName);
        if (lookupWithoutPrefix.equals(productWithoutPrefix)) {
            return 9_000;
        }
        if (productName.contains(lookup)) {
            return 5_000 + lookup.length();
        }
        if (lookup.contains(productName)) {
            return 4_000 + productName.length();
        }
        if (productWithoutPrefix.contains(lookupWithoutPrefix)) {
            return 3_000 + lookupWithoutPrefix.length();
        }
        if (lookupWithoutPrefix.contains(productWithoutPrefix)) {
            return 2_000 + productWithoutPrefix.length();
        }
        return 0;
    }

    private String removeRicePrefix(String value) {
        return value.replaceFirst("^gao\\s+", "").trim();
    }

    private String normalizeProductLookupText(String value) {
        return cleanRiceTypeText(normalizeVietnameseText(value))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private BigDecimal parseQuantityKg(String quantity) {
        if (quantity == null) {
            return null;
        }
        Matcher matcher = FIRST_NUMBER_PATTERN.matcher(quantity.replace(',', '.'));
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String cleanRiceTypeText(String value) {
        if (value == null) {
            return "";
        }
        return QUANTITY_TEXT_PATTERN.matcher(value)
                .replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildProductDetailsJson(PendingChatOrder draft, OrderPricing pricing) {
        Map<String, Object> details = new LinkedHashMap<>();
        AiParsedOrder parsed = draft.toParsedOrder();
        details.put("rice_type", parsed.getRiceType());
        details.put("quantity", parsed.getQuantity());
        details.put("address", parsed.getAddress());
        details.put("customer_phone", parsed.getCustomerPhone());
        if (draft.getLoyaltyPhone() != null && !draft.getLoyaltyPhone().isBlank()) {
            details.put("loyalty_phone", draft.getLoyaltyPhone());
        }
        details.put("items", pricing.itemsForJson());
        details.put("total_price", pricing.totalPrice());
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
        String loyaltyNote = (draft.getLoyaltyPhone() != null && !draft.getLoyaltyPhone().isBlank())
                ? " SĐT tích điểm: " + draft.getLoyaltyPhone() + "."
                : "";
        return String.format(
                "Mình đã chốt đơn #%s gồm %s, giao đến %s. SĐT liên hệ: %s.%s Cửa hàng sẽ sớm xử lí cho bạn nhé.",
                order.getId(),
                draft.summaryForMessage(),
                parsed.getAddress(),
                parsed.getCustomerPhone(),
                loyaltyNote);
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

    private String buildLoyaltyPhoneRequest(PendingChatOrder draft) {
        return "Mình đã ghi nhận đơn gồm " + draft.summaryForMessage() + ". "
                + "Bạn có SĐT tích điểm không? Nếu có, bạn cho mình xin SĐT nhé (nhắn 'bỏ qua' nếu không muốn tích điểm).";
    }

    private String extractPhone(String normalizedText) {
        Matcher m = PHONE_PATTERN.matcher(normalizedText.replaceAll("\\s+", ""));
        return m.find() ? m.group() : null;
    }

    private static String extractCustomerName(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return null;
        }
        String text = rawText.trim();

        // Remove common prefixes (case insensitive)
        text = text.replaceAll("(?i)^(tôi tên là|tên tôi là|tôi tên|tên tôi|mình tên|tên mình|tên là|tên)\\s+", "");
        text = text.replaceAll("(?i)^(anh|chị|em)\\s+", "");

        // Extract first 1-3 words as name (Vietnamese names are typically 2-4 words)
        String[] words = text.split("\\s+");
        if (words.length == 0) {
            return null;
        }

        // Take up to 3 words, but stop at common location/address indicators
        StringBuilder name = new StringBuilder();
        int wordCount = 0;
        for (String word : words) {
            if (wordCount >= 3) break;
            // Stop if we hit location/address keywords
            if (word.matches("(?i)(ở|tại|địa|chỉ|đường|phường|quận|huyện|tỉnh|thành|phố|cần|giuộc|đốc|cầu).*")) {
                break;
            }
            // Stop if we hit phone number
            if (word.matches("0\\d{9,10}")) {
                break;
            }
            // Stop if we hit common words that indicate end of name
            if (word.matches("(?i)(nhé|ạ|à|nha|nhá)")) {
                break;
            }
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(word);
            wordCount++;
        }

        String result = name.toString().trim();
        // Only return if we got something reasonable (2-50 chars)
        return (result.length() >= 2 && result.length() <= 50) ? result : null;
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
                || isNotBlank(result.getCustomerPhone())
                || isNotBlank(result.getCustomerName());
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
        if (!isNotBlank(draft.customerName)) {
            missingFields.add("tên của bạn");
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

    private static boolean isBopQua(String text) {
        if (text == null) {
            return false;
        }
        String norm = text.trim().toLowerCase();
        return norm.equals("bo qua") || norm.equals("bỏ qua") || norm.equals("skip")
                || norm.equals("khong") || norm.equals("không") || norm.equals("ko")
                || norm.equals("k") || norm.equals("no");
    }

    private static class PendingChatOrder {
        private String riceType;
        private String quantity;
        private String address;
        private String customerPhone;
        private String customerName;
        private String loyaltyPhone;
        private boolean loyaltyPhoneAsked;
        private boolean customerNameAsked;
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
            customerName = chooseNewValue(customerName, result.getCustomerName());

            // If waiting for customer name and AI didn't extract it, try raw text fallback
            if (customerNameAsked && !isNotBlank(customerName) && rawText != null) {
                String extractedName = extractCustomerName(rawText);
                if (extractedName != null && !extractedName.isEmpty()) {
                    customerName = extractedName;
                    customerNameAsked = false; // Mark as collected
                }
            }

            // If waiting for loyalty phone and no phone extracted from AI, try raw text
            if (loyaltyPhoneAsked && !isLoyaltyPhoneCollected()) {
                String raw = rawText != null ? rawText.replaceAll("\\s+", "") : "";
                Matcher m = PHONE_PATTERN.matcher(raw);
                if (m.find()) {
                    loyaltyPhone = m.group();
                } else if (rawText != null && isBopQua(rawText)) {
                    loyaltyPhone = "";
                    loyaltyPhoneAsked = true;
                }
            }
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
                    && isNotBlank(customerPhone)
                    && isNotBlank(customerName);
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

        private boolean isLoyaltyPhoneCollected() {
            return loyaltyPhoneAsked && (loyaltyPhone != null);
        }

        private void setLoyaltyPhone(String phone) {
            this.loyaltyPhone = phone;
            this.loyaltyPhoneAsked = true;
        }

        private String getLoyaltyPhone() {
            return loyaltyPhone;
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

        private List<PendingChatOrderItem> normalizedItemsForOrder() {
            upsertCurrentItem();
            List<PendingChatOrderItem> normalizedItems = new ArrayList<>();
            for (PendingChatOrderItem item : items) {
                normalizedItems.addAll(expandDelimitedItem(item));
            }
            return normalizedItems;
        }

        private List<PendingChatOrderItem> expandDelimitedItem(PendingChatOrderItem item) {
            if (!isNotBlank(item.riceType) || !isNotBlank(item.quantity)) {
                return List.of();
            }

            String[] riceParts = splitDelimitedValues(item.riceType);
            String[] quantityParts = splitDelimitedValues(item.quantity);
            if (riceParts.length <= 1 || quantityParts.length <= 1 || riceParts.length != quantityParts.length) {
                return List.of(item);
            }

            List<PendingChatOrderItem> expandedItems = new ArrayList<>();
            for (int index = 0; index < riceParts.length; index++) {
                if (isNotBlank(riceParts[index]) && isNotBlank(quantityParts[index])) {
                    expandedItems.add(new PendingChatOrderItem(riceParts[index].trim(), quantityParts[index].trim()));
                }
            }
            return expandedItems.isEmpty() ? List.of(item) : expandedItems;
        }

        private String[] splitDelimitedValues(String value) {
            return value.split("\\s*;\\s*");
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

    private record OrderPricing(List<OrderLine> lines, BigDecimal totalPrice) {
        private List<Map<String, Object>> itemsForJson() {
            List<Map<String, Object>> itemMaps = new ArrayList<>();
            for (OrderLine line : lines) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("rice_type", line.riceType());
                itemMap.put("quantity", line.quantityText());
                itemMap.put("quantity_kg", line.quantityKg());
                itemMap.put("product_id", line.product() != null ? line.product().getId() : null);
                itemMap.put("product_name", line.product() != null ? line.product().getName() : null);
                itemMap.put("price_per_kg", line.product() != null ? line.product().getPricePerKg() : null);
                itemMap.put("line_total", line.lineTotal());
                itemMaps.add(itemMap);
            }
            return itemMaps;
        }
    }

    private record OrderLine(
            String riceType,
            String quantityText,
            BigDecimal quantityKg,
            RiceProduct product,
            BigDecimal lineTotal) {
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
