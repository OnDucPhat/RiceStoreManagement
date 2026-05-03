package com.ricestoremanagement.controller;

import com.ricestoremanagement.dto.loyalty.CustomerLoyaltyResponse;
import com.ricestoremanagement.service.LoyaltyService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loyalty")
public class LoyaltyController {
    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @GetMapping
    public List<CustomerLoyaltyResponse> getAll() {
        return loyaltyService.getAll();
    }

    @GetMapping("/{phone}")
    public CustomerLoyaltyResponse getByPhone(@PathVariable String phone) {
        return loyaltyService.getByPhone(phone);
    }

    @PostMapping("/{phone}/give-gift")
    public ResponseEntity<CustomerLoyaltyResponse> giveGift(@PathVariable String phone) {
        CustomerLoyaltyResponse response = loyaltyService.giveGift(phone);
        return ResponseEntity.ok(response);
    }
}
