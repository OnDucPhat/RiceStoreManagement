package com.ricestoremanagement.dto.messenger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {
    private String object;
    private List<WebhookEntry> entry;

    public WebhookPayload() {
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<WebhookEntry> getEntry() {
        return entry;
    }

    public void setEntry(List<WebhookEntry> entry) {
        this.entry = entry;
    }
}
