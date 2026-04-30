package com.ricestoremanagement.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class OrderCreateRequest {
    @JsonProperty("customer_name")
    @NotBlank
    @Size(max = 200)
    private String customerName;

    @NotBlank
    @Size(max = 500)
    private String address;

    @JsonProperty("product_details")
    @NotBlank
    private String productDetails;

    @JsonProperty("total_price")
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal totalPrice;

    public OrderCreateRequest() {
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getProductDetails() {
        return productDetails;
    }

    public void setProductDetails(String productDetails) {
        this.productDetails = productDetails;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
}
