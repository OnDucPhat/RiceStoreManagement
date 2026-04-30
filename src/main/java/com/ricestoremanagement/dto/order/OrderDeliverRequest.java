package com.ricestoremanagement.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class OrderDeliverRequest {
    @JsonProperty("shipper_id")
    @NotNull
    private Long shipperId;

    public OrderDeliverRequest() {
    }

    public Long getShipperId() {
        return shipperId;
    }

    public void setShipperId(Long shipperId) {
        this.shipperId = shipperId;
    }
}
