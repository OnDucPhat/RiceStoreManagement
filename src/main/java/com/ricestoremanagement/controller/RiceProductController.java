package com.ricestoremanagement.controller;

import com.ricestoremanagement.dto.riceproduct.RiceProductRequest;
import com.ricestoremanagement.dto.riceproduct.RiceProductResponse;
import com.ricestoremanagement.model.RiceProduct;
import com.ricestoremanagement.service.RiceProductService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rice-products")
public class RiceProductController {
    private final RiceProductService riceProductService;

    public RiceProductController(RiceProductService riceProductService) {
        this.riceProductService = riceProductService;
    }

    @GetMapping
    public List<RiceProductResponse> getProducts(
            @RequestParam(name = "active_only", required = false) Boolean activeOnly) {
        return riceProductService.getProducts(activeOnly).stream()
                .map(RiceProductResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public RiceProductResponse getProduct(@PathVariable Long id) {
        return RiceProductResponse.from(riceProductService.getProduct(id));
    }

    @PostMapping
    public ResponseEntity<RiceProductResponse> createProduct(@Valid @RequestBody RiceProductRequest request) {
        RiceProduct product = riceProductService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RiceProductResponse.from(product));
    }

    @PutMapping("/{id}")
    public RiceProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody RiceProductRequest request) {
        return RiceProductResponse.from(riceProductService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/active")
    public RiceProductResponse setActive(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> request) {
        Boolean active = request != null ? request.get("active") : null;
        if (active == null) {
            throw new IllegalArgumentException("active is required");
        }
        return RiceProductResponse.from(riceProductService.setActive(id, active));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        riceProductService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }
}
