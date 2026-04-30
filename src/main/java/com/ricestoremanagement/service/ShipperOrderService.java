package com.ricestoremanagement.service;

import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ShipperOrderService {
    private final OrderRepository orderRepository;

    public ShipperOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> getAssignedOrders(Long shipperId, OrderStatus status) {
        if (shipperId == null) {
            throw new IllegalArgumentException("shipperId is required");
        }
        if (status == null) {
            return orderRepository.findByShipperIdOrderByIdDesc(shipperId);
        }
        return orderRepository.findByShipperIdAndStatusOrderByIdDesc(shipperId, status);
    }

    public Order markDelivered(Long orderId, Long shipperId) {
        if (shipperId == null) {
            throw new IllegalArgumentException("shipperId is required");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getShipper() == null || !shipperId.equals(order.getShipper().getId())) {
            throw new IllegalArgumentException("Order not assigned to this shipper");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Order status must be PENDING to deliver");
        }

        order.setStatus(OrderStatus.DELIVERED_WAITING_HANDOVER);
        return orderRepository.save(order);
    }
}
