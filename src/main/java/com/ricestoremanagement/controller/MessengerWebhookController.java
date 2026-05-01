package com.ricestoremanagement.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricestoremanagement.dto.ai.AiChatbotResult;
import com.ricestoremanagement.dto.ai.AiParsedOrder;
import com.ricestoremanagement.dto.messenger.WebhookEntry;
import com.ricestoremanagement.dto.messenger.WebhookMessaging;
import com.ricestoremanagement.dto.messenger.WebhookPayload;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.enums.OrderSource;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.repository.OrderRepository;
import com.ricestoremanagement.service.AiParsingService;
import com.ricestoremanagement.service.MessengerGraphApiService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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

    private final String verifyToken;
    private final AiParsingService aiParsingService;
    private final OrderRepository orderRepository;
    private final MessengerGraphApiService messengerGraphApiService;
    private final ObjectMapper objectMapper;

    public MessengerWebhookController(
            @Value("${meta.webhook.verify-token:}") String verifyToken,
            AiParsingService aiParsingService,
            OrderRepository orderRepository,
            MessengerGraphApiService messengerGraphApiService,
            ObjectMapper objectMapper) {
        this.verifyToken = verifyToken;
        this.aiParsingService = aiParsingService;
        this.orderRepository = orderRepository;
        this.messengerGraphApiService = messengerGraphApiService;
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
        if (payload == null || payload.getEntry() == null) {
            return ResponseEntity.ok("EVENT_RECEIVED");
        }

        for (WebhookEntry entry : payload.getEntry()) {
            if (entry.getMessaging() == null) {
                continue;
            }
            for (WebhookMessaging messaging : entry.getMessaging()) {
                handleIncomingMessage(messaging);
            }
        }

        return ResponseEntity.ok("EVENT_RECEIVED");
    }

    private void handleIncomingMessage(WebhookMessaging messaging) {
        if (messaging == null || messaging.getMessage() == null) {
            return;
        }

        String senderId = messaging.getSender() != null ? messaging.getSender().getId() : null;
        String text = messaging.getMessage().getText();
        if (senderId == null || senderId.trim().isEmpty() || text == null || text.trim().isEmpty()) {
            return;
        }

        Optional<AiChatbotResult> resultOpt = aiParsingService.chat(text);
        if (resultOpt.isEmpty()) {
            messengerGraphApiService.sendTextMessage(senderId, fallbackReply());
            return;
        }

        AiChatbotResult result = resultOpt.get();
        if (!result.isOrderIntent()) {
            messengerGraphApiService.sendTextMessage(
                    senderId,
                    result.replyOrDefault(fallbackReply()));
            return;
        }

        if (!result.isCompleteOrder()) {
            messengerGraphApiService.sendTextMessage(
                    senderId,
                    result.replyOrDefault("Bạn cho mình xin thêm loại gạo, số lượng và địa chỉ giao hàng nhé."));
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

        orderRepository.save(order);

        messengerGraphApiService.sendTextMessage(
                senderId,
                result.replyOrDefault(buildConfirmationMessage(parsed)));

        log.info("Created Messenger order from sender {}", senderId);
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
                "Mình đã ghi nhận đơn %s, số lượng %s, giao đến %s. Cửa hàng sẽ xử lý sớm nhé.",
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
        return "Mình là trợ lý của cửa hàng gạo. Bạn có thể hỏi về gạo hoặc nhắn đơn hàng, ví dụ: 2kg ST25 giao tới Quận 10.";
    }
}
