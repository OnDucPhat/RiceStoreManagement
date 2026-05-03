package com.ricestoremanagement.dto.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class StockImportRequest {
    @NotNull
    @JsonProperty("product_id")
    private Long productId;

    @NotNull
    @DecimalMin("0.01")
    @JsonProperty("quantity_kg")
    private BigDecimal quantityKg;

    @NotNull
    @DecimalMin("0.0")
    @JsonProperty("cost_per_kg")
    private BigDecimal costPerKg;

    public StockImportRequest() {
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }

    public BigDecimal getCostPerKg() { return costPerKg; }
    public void setCostPerKg(BigDecimal costPerKg) { this.costPerKg = costPerKg; }
}
