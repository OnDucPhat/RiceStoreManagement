package com.ricestoremanagement.dto.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ricestoremanagement.model.StockEntry;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StockEntryResponse {
    private Long id;

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("quantity_kg")
    private BigDecimal quantityKg;

    @JsonProperty("cost_per_kg")
    private BigDecimal costPerKg;

    @JsonProperty("imported_at")
    private LocalDateTime importedAt;

    public StockEntryResponse() {
    }

    public static StockEntryResponse from(StockEntry entry) {
        StockEntryResponse r = new StockEntryResponse();
        r.setId(entry.getId());
        r.setProductId(entry.getProduct().getId());
        r.setProductName(entry.getProduct().getName());
        r.setQuantityKg(entry.getQuantityKg());
        r.setCostPerKg(entry.getCostPerKg());
        r.setImportedAt(entry.getImportedAt());
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }

    public BigDecimal getCostPerKg() { return costPerKg; }
    public void setCostPerKg(BigDecimal costPerKg) { this.costPerKg = costPerKg; }

    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
}
