package com.ricestoremanagement.controller;

import com.ricestoremanagement.dto.order.OrderAssignShipperRequest;
import com.ricestoremanagement.dto.order.OrderCreateRequest;
import com.ricestoremanagement.dto.order.OrderResponse;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam(name = "status", required = false) OrderStatus status) {
        List<Order> orders = orderService.getOrders(status);
        return orders.stream().map(OrderResponse::from).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        Order order = orderService.createManualOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @PutMapping("/{id}/shipper")
    public ResponseEntity<OrderResponse> assignShipper(
            @PathVariable Long id,
            @Valid @RequestBody OrderAssignShipperRequest request) {
        Order order = orderService.assignShipper(id, request.getShipperId());
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
