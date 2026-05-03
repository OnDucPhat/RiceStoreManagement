package com.ricestoremanagement.service;

import com.ricestoremanagement.dto.handover.HandoverConfirmRequest;
import com.ricestoremanagement.exception.HandoverValidationException;
import com.ricestoremanagement.model.AuditLog;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.User;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.model.enums.UserRole;
import com.ricestoremanagement.repository.AuditLogRepository;
import com.ricestoremanagement.repository.OrderRepository;
import com.ricestoremanagement.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoverService {
    private static final Logger log = LoggerFactory.getLogger(HandoverService.class);
    private static final String ACTION_HANDOVER_CONFIRM = "HANDOVER_CONFIRM";

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final InventoryService inventoryService;
    private final LoyaltyService loyaltyService;
    private final ObjectMapper objectMapper;

    public HandoverService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            InventoryService inventoryService,
            LoyaltyService loyaltyService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.inventoryService = inventoryService;
        this.loyaltyService = loyaltyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Order> confirmHandover(HandoverConfirmRequest request) {
        validateRequest(request);

        User admin = getUser(request.getAdminId(), "Admin not found");
        if (admin.getRole() != UserRole.ADMIN) {
            throw new HandoverValidationException("User is not an admin");
        }

        User shipper = getUser(request.getShipperId(), "Shipper not found");
        if (shipper.getRole() != UserRole.SHIPPER) {
            throw new HandoverValidationException("User is not a shipper");
        }

        Set<Long> orderIds = new LinkedHashSet<>(request.getOrderIds());
        List<Order> orders = orderRepository.findAllById(orderIds);
        if (orders.size() != orderIds.size()) {
            Set<Long> foundIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
            List<Long> missing = orderIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new HandoverValidationException("Orders not found: " + missing);
        }

        List<AuditLog> auditLogs = new ArrayList<>();
        for (Order order : orders) {
            if (order.getStatus() != OrderStatus.DELIVERED_WAITING_HANDOVER) {
                throw new HandoverValidationException(
                        "Order " + order.getId() + " is not ready for handover");
            }
            if (order.getShipper() == null || !shipper.getId().equals(order.getShipper().getId())) {
                throw new HandoverValidationException(
                        "Order " + order.getId() + " is not assigned to this shipper");
            }

            order.setStatus(OrderStatus.COMPLETED);
            auditLogs.add(buildAuditLog(order, admin, shipper));
        }

        orderRepository.saveAll(orders);
        auditLogRepository.saveAll(auditLogs);

        // After saving, trigger loyalty points and stock deduction for each completed order
        for (Order order : orders) {
            try {
                processCompletedOrder(order);
            } catch (Exception ex) {
                log.warn("Failed to process loyalty/stock for order {}: {}", order.getId(), ex.getMessage());
            }
        }

        return orders;
    }

    /**
     * Parse product_details JSON and apply loyalty points + stock deduction.
     * product_details may have an "items" array: [{product_id, quantity_kg, ...}]
     * or a flat structure with product_id and quantity_kg.
     * Uses loyalty_phone if present, otherwise falls back to customer_phone.
     */
    private void processCompletedOrder(Order order) {
        if (order.getProductDetails() == null || order.getProductDetails().isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(order.getProductDetails());
            // Prefer loyalty_phone from chatbot flow; fall back to customer_phone
            String loyaltyPhone = root.path("loyalty_phone").asText(null);
            String phone = (loyaltyPhone != null && !loyaltyPhone.isBlank())
                    ? loyaltyPhone : order.getCustomerPhone();

            JsonNode itemsNode = root.path("items");
            if (itemsNode.isArray() && !itemsNode.isEmpty()) {
                BigDecimal totalKg = BigDecimal.ZERO;
                for (JsonNode item : itemsNode) {
                    Long productId = item.path("product_id").isNull() ? null : item.path("product_id").asLong();
                    BigDecimal qtyKg = item.path("quantity_kg").isNull() ? null
                            : new BigDecimal(item.path("quantity_kg").asText());
                    if (productId != null && qtyKg != null) {
                        inventoryService.deductStockForOrder(productId, qtyKg);
                        totalKg = totalKg.add(qtyKg);
                    }
                }
                loyaltyService.addPoints(phone, totalKg);
            } else {
                // Flat structure fallback
                Long productId = root.path("product_id").isNull() ? null : root.path("product_id").asLong();
                BigDecimal qtyKg = root.path("quantity_kg").isNull() ? null
                        : new BigDecimal(root.path("quantity_kg").asText());
                if (productId != null && qtyKg != null) {
                    inventoryService.deductStockForOrder(productId, qtyKg);
                }
                loyaltyService.addPoints(phone, qtyKg != null ? qtyKg : BigDecimal.ZERO);
            }
        } catch (Exception ex) {
            log.warn("Could not parse product_details for order {}: {}", order.getId(), ex.getMessage());
        }
    }

    private void validateRequest(HandoverConfirmRequest request) {
        if (request == null) {
            throw new HandoverValidationException("Request body is required");
        }
        if (request.getOrderIds() == null || request.getOrderIds().isEmpty()) {
            throw new HandoverValidationException("orderIds is required");
        }
    }

    private User getUser(Long userId, String message) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new HandoverValidationException(message));
    }

    private AuditLog buildAuditLog(Order order, User admin, User shipper) {
        AuditLog log = new AuditLog();
        log.setActionType(ACTION_HANDOVER_CONFIRM);
        log.setUser(admin);
        log.setTargetOrder(order);
        log.setDescription(buildDescription(order, shipper));
        return log;
    }

    private String buildDescription(Order order, User shipper) {
        BigDecimal amount = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
        return "Handover confirmed. ShipperId=" + shipper.getId()
                + ", Shipper=" + shipper.getUsername()
                + ", Amount=" + amount
                + ", OrderId=" + order.getId();
    }
}
