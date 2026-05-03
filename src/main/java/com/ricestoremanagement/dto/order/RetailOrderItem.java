package com.ricestoremanagement.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class RetailOrderItem {

    @JsonProperty("product_id")
    @NotNull(message = "product_id không được trống")
    private Long productId;

    @JsonProperty("quantity_kg")
    @NotNull(message = "quantity_kg không được trống")
    @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
    private BigDecimal quantityKg;

    public RetailOrderItem() {
    }

    public RetailOrderItem(Long productId, BigDecimal quantityKg) {
        this.productId = productId;
        this.quantityKg = quantityKg;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public void setQuantityKg(BigDecimal quantityKg) {
        this.quantityKg = quantityKg;
    }
}
