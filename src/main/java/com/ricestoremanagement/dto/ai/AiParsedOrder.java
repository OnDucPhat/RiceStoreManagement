package com.ricestoremanagement.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiParsedOrder {
    @JsonProperty("rice_type")
    private String riceType;
    private String quantity;
    private String address;
    @JsonProperty("customer_phone")
    private String customerPhone;

    public AiParsedOrder() {
    }

    public String getRiceType() {
        return riceType;
    }

    public void setRiceType(String riceType) {
        this.riceType = riceType;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public boolean isComplete() {
        return isNotBlank(riceType)
                && isNotBlank(quantity)
                && isNotBlank(address)
                && isNotBlank(customerPhone);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
