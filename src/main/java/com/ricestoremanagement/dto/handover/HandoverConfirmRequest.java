package com.ricestoremanagement.dto.handover;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class HandoverConfirmRequest {
    @JsonProperty("admin_id")
    @NotNull
    private Long adminId;

    @JsonProperty("shipper_id")
    @NotNull
    private Long shipperId;

    @JsonProperty("order_ids")
    @NotEmpty
    private List<Long> orderIds;

    public HandoverConfirmRequest() {
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public Long getShipperId() {
        return shipperId;
    }

    public void setShipperId(Long shipperId) {
        this.shipperId = shipperId;
    }

    public List<Long> getOrderIds() {
        return orderIds;
    }

    public void setOrderIds(List<Long> orderIds) {
        this.orderIds = orderIds;
    }
}
