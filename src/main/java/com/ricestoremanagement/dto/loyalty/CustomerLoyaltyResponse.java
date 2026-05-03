package com.ricestoremanagement.dto.loyalty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ricestoremanagement.model.CustomerLoyalty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CustomerLoyaltyResponse {
    private Long id;
    private String phone;

    @JsonProperty("total_points")
    private BigDecimal totalPoints;

    @JsonProperty("purchase_count")
    private int purchaseCount;

    @JsonProperty("last_reset_at")
    private LocalDateTime lastResetAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public CustomerLoyaltyResponse() {
    }

    public static CustomerLoyaltyResponse from(CustomerLoyalty loyalty) {
        CustomerLoyaltyResponse r = new CustomerLoyaltyResponse();
        r.setId(loyalty.getId());
        r.setPhone(loyalty.getPhone());
        r.setTotalPoints(loyalty.getTotalPoints());
        r.setPurchaseCount(loyalty.getPurchaseCount());
        r.setLastResetAt(loyalty.getLastResetAt());
        r.setCreatedAt(loyalty.getCreatedAt());
        r.setUpdatedAt(loyalty.getUpdatedAt());
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public BigDecimal getTotalPoints() { return totalPoints; }
    public void setTotalPoints(BigDecimal totalPoints) { this.totalPoints = totalPoints; }

    public int getPurchaseCount() { return purchaseCount; }
    public void setPurchaseCount(int purchaseCount) { this.purchaseCount = purchaseCount; }

    public LocalDateTime getLastResetAt() { return lastResetAt; }
    public void setLastResetAt(LocalDateTime lastResetAt) { this.lastResetAt = lastResetAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
