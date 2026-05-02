package com.ricestoremanagement.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatMessageResponse {
    @JsonProperty("session_id")
    private String sessionId;

    private String reply;

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("order_created")
    private boolean orderCreated;

    private String outcome;

    public ChatMessageResponse() {
    }

    public ChatMessageResponse(
            String sessionId,
            String reply,
            Long orderId,
            boolean orderCreated,
            String outcome) {
        this.sessionId = sessionId;
        this.reply = reply;
        this.orderId = orderId;
        this.orderCreated = orderCreated;
        this.outcome = outcome;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public boolean isOrderCreated() {
        return orderCreated;
    }

    public void setOrderCreated(boolean orderCreated) {
        this.orderCreated = orderCreated;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
