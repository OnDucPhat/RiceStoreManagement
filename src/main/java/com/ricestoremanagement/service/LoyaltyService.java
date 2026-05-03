package com.ricestoremanagement.service;

import com.ricestoremanagement.dto.loyalty.CustomerLoyaltyResponse;
import com.ricestoremanagement.model.CustomerLoyalty;
import com.ricestoremanagement.repository.CustomerLoyaltyRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoyaltyService {
    private final CustomerLoyaltyRepository loyaltyRepository;

    public LoyaltyService(CustomerLoyaltyRepository loyaltyRepository) {
        this.loyaltyRepository = loyaltyRepository;
    }

    /**
     * Add points for a completed order. 1 kg = 1 point.
     * Creates the loyalty record if it doesn't exist yet.
     */
    @Transactional
    public void addPoints(String phone, BigDecimal quantityKg) {
        if (phone == null || phone.isBlank() || quantityKg == null || quantityKg.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String normalizedPhone = phone.trim();
        CustomerLoyalty loyalty = loyaltyRepository.findByPhone(normalizedPhone)
                .orElseGet(() -> {
                    CustomerLoyalty newRecord = new CustomerLoyalty();
                    newRecord.setPhone(normalizedPhone);
                    return newRecord;
                });

        loyalty.setTotalPoints(loyalty.getTotalPoints().add(quantityKg));
        loyalty.setPurchaseCount(loyalty.getPurchaseCount() + 1);
        loyaltyRepository.save(loyalty);
    }

    /**
     * Mark gift as given: reset points and purchase_count to 0.
     */
    @Transactional
    public CustomerLoyaltyResponse giveGift(String phone) {
        CustomerLoyalty loyalty = loyaltyRepository.findByPhone(phone.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy SĐT: " + phone));

        loyalty.setTotalPoints(BigDecimal.ZERO);
        loyalty.setPurchaseCount(0);
        loyalty.setLastResetAt(LocalDateTime.now());
        loyaltyRepository.save(loyalty);
        return CustomerLoyaltyResponse.from(loyalty);
    }

    public CustomerLoyaltyResponse getByPhone(String phone) {
        CustomerLoyalty loyalty = loyaltyRepository.findByPhone(phone.trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy SĐT: " + phone));
        return CustomerLoyaltyResponse.from(loyalty);
    }

    public List<CustomerLoyaltyResponse> getAll() {
        return loyaltyRepository.findAll(Sort.by(Sort.Direction.DESC, "totalPoints"))
                .stream()
                .map(CustomerLoyaltyResponse::from)
                .collect(Collectors.toList());
    }
}
