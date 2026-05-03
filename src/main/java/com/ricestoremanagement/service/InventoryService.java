package com.ricestoremanagement.service;

import com.ricestoremanagement.dto.inventory.StockEntryResponse;
import com.ricestoremanagement.dto.inventory.StockImportRequest;
import com.ricestoremanagement.dto.riceproduct.RiceProductResponse;
import com.ricestoremanagement.model.RiceProduct;
import com.ricestoremanagement.model.StockEntry;
import com.ricestoremanagement.repository.RiceProductRepository;
import com.ricestoremanagement.repository.StockEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final RiceProductRepository riceProductRepository;
    private final StockEntryRepository stockEntryRepository;

    public InventoryService(
            RiceProductRepository riceProductRepository,
            StockEntryRepository stockEntryRepository) {
        this.riceProductRepository = riceProductRepository;
        this.stockEntryRepository = stockEntryRepository;
    }

    /**
     * Import stock for a product.
     * Recalculates cost_per_kg using weighted average:
     *   newCost = (oldStock * oldCost + qty * importCost) / (oldStock + qty)
     */
    @Transactional
    public RiceProductResponse importStock(StockImportRequest request) {
        RiceProduct product = riceProductRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Rice product not found"));

        BigDecimal oldStock = product.getStockKg();
        BigDecimal oldCost = product.getCostPerKg();
        BigDecimal qty = request.getQuantityKg();
        BigDecimal importCost = request.getCostPerKg();

        // Weighted average cost
        BigDecimal newStock = oldStock.add(qty);
        BigDecimal newCost;
        if (oldStock.compareTo(BigDecimal.ZERO) == 0) {
            newCost = importCost;
        } else {
            BigDecimal totalValue = oldStock.multiply(oldCost).add(qty.multiply(importCost));
            newCost = totalValue.divide(newStock, 2, RoundingMode.HALF_UP);
        }

        product.setStockKg(newStock);
        product.setCostPerKg(newCost);
        riceProductRepository.save(product);

        // Save import entry for history
        StockEntry entry = new StockEntry();
        entry.setProduct(product);
        entry.setQuantityKg(qty);
        entry.setCostPerKg(importCost);
        stockEntryRepository.save(entry);

        return RiceProductResponse.from(product);
    }

    /**
     * Deduct stock when a sale is completed (called from HandoverService).
     * Tries to parse product_id and quantity_kg from product_details JSON.
     * Falls back gracefully if parsing fails.
     */
    @Transactional
    public void deductStockForOrder(Long productId, BigDecimal quantityKg) {
        if (productId == null || quantityKg == null || quantityKg.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        riceProductRepository.findById(productId).ifPresent(product -> {
            BigDecimal current = product.getStockKg();
            BigDecimal updated = current.subtract(quantityKg);
            if (updated.compareTo(BigDecimal.ZERO) < 0) {
                updated = BigDecimal.ZERO;
            }
            product.setStockKg(updated);
            riceProductRepository.save(product);
        });
    }

    public List<StockEntryResponse> getHistory(Long productId) {
        List<StockEntry> entries = stockEntryRepository.findByProductIdOrderByImportedAtDesc(productId);
        return entries.stream().map(StockEntryResponse::from).collect(Collectors.toList());
    }
}
