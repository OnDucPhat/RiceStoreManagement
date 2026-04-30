package com.ricestoremanagement.dto.messenger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookEntry {
    private String id;
    private Long time;
    private List<WebhookMessaging> messaging;

    public WebhookEntry() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public List<WebhookMessaging> getMessaging() {
        return messaging;
    }

    public void setMessaging(List<WebhookMessaging> messaging) {
        this.messaging = messaging;
    }
}
