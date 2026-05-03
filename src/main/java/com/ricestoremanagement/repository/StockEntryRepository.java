package com.ricestoremanagement.repository;

import com.ricestoremanagement.model.StockEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockEntryRepository extends JpaRepository<StockEntry, Long> {
    List<StockEntry> findByProductIdOrderByImportedAtDesc(Long productId);
}
