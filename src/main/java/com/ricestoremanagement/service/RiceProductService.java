package com.ricestoremanagement.service;

import com.ricestoremanagement.dto.riceproduct.RiceProductRequest;
import com.ricestoremanagement.model.RiceProduct;
import com.ricestoremanagement.repository.RiceProductRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RiceProductService {
    private final RiceProductRepository riceProductRepository;

    public RiceProductService(RiceProductRepository riceProductRepository) {
        this.riceProductRepository = riceProductRepository;
    }

    public List<RiceProduct> getProducts(Boolean activeOnly) {
        Sort sort = Sort.by(Sort.Direction.ASC, "name");
        if (Boolean.TRUE.equals(activeOnly)) {
            return riceProductRepository.findByActiveTrue(sort);
        }
        return riceProductRepository.findAll(sort);
    }

    public RiceProduct getProduct(Long id) {
        return riceProductRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rice product not found"));
    }

    public RiceProduct createProduct(RiceProductRequest request) {
        String name = normalize(request.getName());
        if (riceProductRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Rice product name already exists");
        }

        RiceProduct product = new RiceProduct();
        applyRequest(product, request, name);
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        return riceProductRepository.save(product);
    }

    public RiceProduct updateProduct(Long id, RiceProductRequest request) {
        RiceProduct product = getProduct(id);
        String name = normalize(request.getName());
        if (riceProductRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Rice product name already exists");
        }

        applyRequest(product, request, name);
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        return riceProductRepository.save(product);
    }

    public RiceProduct setActive(Long id, boolean active) {
        RiceProduct product = getProduct(id);
        product.setActive(active);
        return riceProductRepository.save(product);
    }

    public void deactivateProduct(Long id) {
        setActive(id, false);
    }

    private void applyRequest(RiceProduct product, RiceProductRequest request, String normalizedName) {
        product.setName(normalizedName);
        product.setCharacteristics(request.getCharacteristics().trim());
        product.setPricePerKg(request.getPricePerKg());
        product.setCostPerKg(request.getCostPerKg());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
