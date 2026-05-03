package com.ricestoremanagement.repository;

import com.ricestoremanagement.model.CustomerLoyalty;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerLoyaltyRepository extends JpaRepository<CustomerLoyalty, Long> {
    Optional<CustomerLoyalty> findByPhone(String phone);

    List<CustomerLoyalty> findAll(Sort sort);
}
