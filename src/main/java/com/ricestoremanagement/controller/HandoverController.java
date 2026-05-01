package com.ricestoremanagement.controller;

import com.ricestoremanagement.dto.handover.HandoverConfirmRequest;
import com.ricestoremanagement.dto.order.OrderResponse;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.service.HandoverService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/handover")
public class HandoverController {
    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<List<OrderResponse>> confirm(@Valid @RequestBody HandoverConfirmRequest request) {
        List<Order> orders = handoverService.confirmHandover(request);
        List<OrderResponse> response = orders.stream().map(OrderResponse::from).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
