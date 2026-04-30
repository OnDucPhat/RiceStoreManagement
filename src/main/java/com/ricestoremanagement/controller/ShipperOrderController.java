package com.ricestoremanagement.controller;

import com.ricestoremanagement.dto.order.OrderDeliverRequest;
import com.ricestoremanagement.dto.order.OrderResponse;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.enums.OrderStatus;
import com.ricestoremanagement.service.ShipperOrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ShipperOrderController {
    private final ShipperOrderService shipperOrderService;

    public ShipperOrderController(ShipperOrderService shipperOrderService) {
        this.shipperOrderService = shipperOrderService;
    }

    @GetMapping("/shippers/{shipperId}/orders")
    public List<OrderResponse> getAssignedOrders(
            @PathVariable Long shipperId,
            @RequestParam(name = "status", required = false) OrderStatus status) {
        List<Order> orders = shipperOrderService.getAssignedOrders(shipperId, status);
        return orders.stream().map(OrderResponse::from).collect(Collectors.toList());
    }

    @PutMapping("/orders/{id}/deliver")
    public ResponseEntity<OrderResponse> markDelivered(
            @PathVariable Long id,
            @Valid @RequestBody OrderDeliverRequest request) {
        Order order = shipperOrderService.markDelivered(id, request.getShipperId());
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
