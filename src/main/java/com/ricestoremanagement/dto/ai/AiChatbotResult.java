package com.ricestoremanagement.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatbotResult {
    private String intent;

    @JsonProperty("rice_type")
    private String riceType;

    private String quantity;
    private String address;

    @JsonProperty("customer_phone")
    private String customerPhone;

    private String reply;

    public AiChatbotResult() {
    }

    public boolean isOrderIntent() {
        return "ORDER_CREATE".equalsIgnoreCase(intent) || "ORDER".equalsIgnoreCase(intent);
    }

    public boolean isCompleteOrder() {
        return isNotBlank(riceType) && isNotBlank(quantity) && isNotBlank(address);
    }

    public String replyOrDefault(String fallback) {
        if (isNotBlank(reply)) {
            return reply.trim();
        }
        return fallback;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
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

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }
}
