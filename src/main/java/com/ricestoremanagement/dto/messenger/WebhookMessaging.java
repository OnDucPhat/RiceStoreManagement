package com.ricestoremanagement.dto.messenger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookMessaging {
    private WebhookSender sender;
    private WebhookMessage message;

    public WebhookMessaging() {
    }

    public WebhookSender getSender() {
        return sender;
    }

    public void setSender(WebhookSender sender) {
        this.sender = sender;
    }

    public WebhookMessage getMessage() {
        return message;
    }

    public void setMessage(WebhookMessage message) {
        this.message = message;
    }
}
