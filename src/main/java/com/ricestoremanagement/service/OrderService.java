package com.ricestoremanagement.service;

import com.ricestoremanagement.dto.order.OrderCreateRequest;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.User;
import com.ricestoremanagement.model.enums.OrderSource;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.model.enums.UserRole;
import com.ricestoremanagement.repository.OrderRepository;
import com.ricestoremanagement.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
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
}
