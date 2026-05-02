package com.ricestoremanagement.service;

import com.ricestoremanagement.dto.order.OrderCreateRequest;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.enums.OrderSource;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.repository.OrderRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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
}
