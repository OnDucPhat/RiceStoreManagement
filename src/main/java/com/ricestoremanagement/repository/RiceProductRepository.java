package com.ricestoremanagement.repository;

import com.ricestoremanagement.model.RiceProduct;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiceProductRepository extends JpaRepository<RiceProduct, Long> {
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<RiceProduct> findByActiveTrue(Sort sort);
}
