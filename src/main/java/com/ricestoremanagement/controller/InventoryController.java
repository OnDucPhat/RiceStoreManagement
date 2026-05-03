package com.ricestoremanagement.controller;

import com.ricestoremanagement.dto.inventory.StockEntryResponse;
import com.ricestoremanagement.dto.inventory.StockImportRequest;
import com.ricestoremanagement.dto.riceproduct.RiceProductResponse;
import com.ricestoremanagement.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/import")
    public ResponseEntity<RiceProductResponse> importStock(@Valid @RequestBody StockImportRequest request) {
        RiceProductResponse response = inventoryService.importStock(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{productId}")
    public List<StockEntryResponse> getHistory(@PathVariable Long productId) {
        return inventoryService.getHistory(productId);
    }
}
