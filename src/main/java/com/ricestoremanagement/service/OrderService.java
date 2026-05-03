package com.ricestoremanagement.service;

import com.ricestoremanagement.dto.order.OrderCreateRequest;
import com.ricestoremanagement.dto.order.RetailOrderItem;
import com.ricestoremanagement.dto.order.RetailOrderRequest;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.RiceProduct;
import com.ricestoremanagement.model.User;
import com.ricestoremanagement.model.enums.OrderSource;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.model.enums.UserRole;
import com.ricestoremanagement.repository.OrderRepository;
import com.ricestoremanagement.repository.RiceProductRepository;
import com.ricestoremanagement.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RiceProductRepository riceProductRepository;
    private final InventoryService inventoryService;
    private final LoyaltyService loyaltyService;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            RiceProductRepository riceProductRepository,
            InventoryService inventoryService,
            LoyaltyService loyaltyService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.riceProductRepository = riceProductRepository;
        this.inventoryService = inventoryService;
        this.loyaltyService = loyaltyService;
        this.objectMapper = objectMapper;
    }

    public List<Order> getOrders(OrderStatus status) {
        if (status == null) {
            return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        }
        return orderRepository.findByStatusOrderByIdDesc(status);
    }

    public Order createManualOrder(OrderCreateRequest request) {
        Order order = new Order();
        order.setCustomerName(request.getCustomerName().trim());
        order.setCustomerPhone(request.getCustomerPhone().trim());
        order.setAddress(request.getAddress().trim());
        order.setProductDetails(request.getProductDetails().trim());
        order.setTotalPrice(request.getTotalPrice());
        order.setSource(OrderSource.MANUAL);
        order.setStatus(OrderStatus.PENDING);
        return orderRepository.save(order);
    }

    public Order assignShipper(Long orderId, Long shipperId) {
        if (shipperId == null) {
            throw new IllegalArgumentException("shipperId is required");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING orders can be assigned to a shipper");
        }

        User shipper = userRepository.findById(shipperId)
                .orElseThrow(() -> new IllegalArgumentException("Shipper not found"));
        if (shipper.getRole() != UserRole.SHIPPER) {
            throw new IllegalArgumentException("User is not a shipper");
        }

        order.setShipper(shipper);
        return orderRepository.save(order);
    }

    @Transactional
    public Order createRetailOrder(RetailOrderRequest request) {
        List<RetailOrderItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Phải có ít nhất 1 sản phẩm");
        }

        BigDecimal totalPrice = BigDecimal.ZERO;
        List<Map<String, Object>> itemsForJson = new java.util.ArrayList<>();
        BigDecimal totalKg = BigDecimal.ZERO;

        for (RetailOrderItem item : items) {
            RiceProduct product = riceProductRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy sản phẩm id=" + item.getProductId()));
            if (!product.isActive()) {
                throw new IllegalArgumentException("Sản phẩm '" + product.getName() + "' không còn đang bán");
            }

            BigDecimal qtyKg = item.getQuantityKg();
            BigDecimal lineTotal = product.getPricePerKg()
                    .multiply(qtyKg)
                    .setScale(2, RoundingMode.HALF_UP);
            totalPrice = totalPrice.add(lineTotal);
            totalKg = totalKg.add(qtyKg);

            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("product_id", product.getId());
            itemMap.put("product_name", product.getName());
            itemMap.put("quantity_kg", qtyKg);
            itemMap.put("price_per_kg", product.getPricePerKg());
            itemMap.put("line_total", lineTotal);
            itemsForJson.add(itemMap);
        }

        Map<String, Object> productDetailsMap = new LinkedHashMap<>();
        productDetailsMap.put("items", itemsForJson);
        productDetailsMap.put("total_price", totalPrice.setScale(2, RoundingMode.HALF_UP));
        if (request.getLoyaltyPhone() != null && !request.getLoyaltyPhone().isBlank()) {
            productDetailsMap.put("loyalty_phone", request.getLoyaltyPhone().trim());
        }

        String productDetailsJson;
        try {
            productDetailsJson = objectMapper.writeValueAsString(productDetailsMap);
        } catch (Exception ex) {
            productDetailsJson = "{\"items\":" + itemsForJson + "}";
        }

        Order order = new Order();
        order.setCustomerName(request.getCustomerName().trim());
        order.setCustomerPhone(
                request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : null);
        order.setAddress("Tại quầy");
        order.setProductDetails(productDetailsJson);
        order.setTotalPrice(totalPrice.setScale(2, RoundingMode.HALF_UP));
        order.setSource(OrderSource.MANUAL);
        order.setStatus(OrderStatus.COMPLETED);

        Order savedOrder = orderRepository.save(order);

        for (RetailOrderItem item : items) {
            inventoryService.deductStockForOrder(item.getProductId(), item.getQuantityKg());
        }

        String loyaltyPhone = request.getLoyaltyPhone();
        if (loyaltyPhone == null || loyaltyPhone.isBlank()) {
            loyaltyPhone = request.getCustomerPhone();
        }
        if (loyaltyPhone != null && !loyaltyPhone.isBlank()) {
            loyaltyService.addPoints(loyaltyPhone.trim(), totalKg);
        }

        return savedOrder;
    }
}
