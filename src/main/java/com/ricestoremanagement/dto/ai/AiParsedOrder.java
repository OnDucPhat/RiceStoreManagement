package com.ricestoremanagement.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiParsedOrder {
    @JsonProperty("rice_type")
    private String riceType;
    private String quantity;
    private String address;

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

    public boolean isComplete() {
        return isNotBlank(riceType) && isNotBlank(quantity) && isNotBlank(address);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
