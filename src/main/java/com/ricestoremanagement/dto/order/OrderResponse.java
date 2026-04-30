package com.ricestoremanagement.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ricestoremanagement.model.Order;
import com.ricestoremanagement.model.enums.OrderSource;
import com.ricestoremanagement.model.enums.OrderStatus;
import java.math.BigDecimal;

public class OrderResponse {
    private Long id;

    @JsonProperty("customer_name")
    private String customerName;

    private String address;

    @JsonProperty("product_details")
    private String productDetails;

    @JsonProperty("total_price")
    private BigDecimal totalPrice;

    private OrderSource source;

    private OrderStatus status;

    @JsonProperty("shipper_id")
    private Long shipperId;

    public OrderResponse() {
    }

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setCustomerName(order.getCustomerName());
        response.setAddress(order.getAddress());
        response.setProductDetails(order.getProductDetails());
        response.setTotalPrice(order.getTotalPrice());
        response.setSource(order.getSource());
        response.setStatus(order.getStatus());
        if (order.getShipper() != null) {
            response.setShipperId(order.getShipper().getId());
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getProductDetails() {
        return productDetails;
    }

    public void setProductDetails(String productDetails) {
        this.productDetails = productDetails;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public OrderSource getSource() {
        return source;
    }

    public void setSource(OrderSource source) {
        this.source = source;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getShipperId() {
        return shipperId;
    }

    public void setShipperId(Long shipperId) {
        this.shipperId = shipperId;
    }
}
