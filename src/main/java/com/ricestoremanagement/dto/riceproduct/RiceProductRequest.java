package com.ricestoremanagement.dto.riceproduct;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class RiceProductRequest {
    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    private String characteristics;

    @JsonProperty("price_per_kg")
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal pricePerKg;

    @JsonProperty("cost_per_kg")
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal costPerKg;

    private Boolean active;

    public RiceProductRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCharacteristics() {
        return characteristics;
    }

    public void setCharacteristics(String characteristics) {
        this.characteristics = characteristics;
    }

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public void setPricePerKg(BigDecimal pricePerKg) {
        this.pricePerKg = pricePerKg;
    }

    public BigDecimal getCostPerKg() {
        return costPerKg;
    }

    public void setCostPerKg(BigDecimal costPerKg) {
        this.costPerKg = costPerKg;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
