package com.ricestoremanagement.config;

import com.ricestoremanagement.model.User;
import com.ricestoremanagement.model.enums.UserRole;
import com.ricestoremanagement.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("shipperSecurity")
public class ShipperSecurity {
    private final UserRepository userRepository;

    public ShipperSecurity(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isSelf(Authentication authentication, Long shipperId) {
        if (authentication == null || shipperId == null || !authentication.isAuthenticated()) {
            return false;
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        return user != null
                && user.getRole() == UserRole.SHIPPER
                && shipperId.equals(user.getId());
    }
}
