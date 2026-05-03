package com.ricestoremanagement.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class RetailOrderRequest {

    @JsonProperty("customer_name")
    @NotBlank(message = "Tên khách hàng không được trống")
    @Size(max = 200)
    private String customerName;

    @JsonProperty("customer_phone")
    @Size(max = 32)
    private String customerPhone;

    @JsonProperty("loyalty_phone")
    @Size(max = 32)
    private String loyaltyPhone;

    @NotEmpty(message = "Phải có ít nhất 1 sản phẩm")
    @Valid
    private List<RetailOrderItem> items;

    public RetailOrderRequest() {
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getLoyaltyPhone() {
        return loyaltyPhone;
    }

    public void setLoyaltyPhone(String loyaltyPhone) {
        this.loyaltyPhone = loyaltyPhone;
    }

    public List<RetailOrderItem> getItems() {
        return items;
    }

    public void setItems(List<RetailOrderItem> items) {
        this.items = items;
    }
}
