package com.ricestoremanagement.dto.riceproduct;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ricestoremanagement.model.RiceProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RiceProductResponse {
    private Long id;
    private String name;
    private String characteristics;

    @JsonProperty("price_per_kg")
    private BigDecimal pricePerKg;

    @JsonProperty("cost_per_kg")
    private BigDecimal costPerKg;

    @JsonProperty("profit_per_kg")
    private BigDecimal profitPerKg;

    @JsonProperty("stock_kg")
    private BigDecimal stockKg;

    private boolean active;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public RiceProductResponse() {
    }

    public static RiceProductResponse from(RiceProduct product) {
        RiceProductResponse response = new RiceProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setCharacteristics(product.getCharacteristics());
        response.setPricePerKg(product.getPricePerKg());
        response.setCostPerKg(product.getCostPerKg());
        response.setProfitPerKg(product.getProfitPerKg());
        response.setStockKg(product.getStockKg());
        response.setActive(product.isActive());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCharacteristics() {
        return characteristics;
    }

    public void setCharacteristics(String characteristics) {
        this.characteristics = characteristics;
    }

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public void setPricePerKg(BigDecimal pricePerKg) {
        this.pricePerKg = pricePerKg;
    }

    public BigDecimal getCostPerKg() {
        return costPerKg;
    }

    public void setCostPerKg(BigDecimal costPerKg) {
        this.costPerKg = costPerKg;
    }

    public BigDecimal getProfitPerKg() { return profitPerKg; }
    public void setProfitPerKg(BigDecimal profitPerKg) { this.profitPerKg = profitPerKg; }

    public BigDecimal getStockKg() { return stockKg; }
    public void setStockKg(BigDecimal stockKg) { this.stockKg = stockKg; }

    public boolean isActive() { return active; }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
