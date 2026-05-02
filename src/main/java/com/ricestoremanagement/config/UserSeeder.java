package com.ricestoremanagement.config;

import com.ricestoremanagement.model.User;
import com.ricestoremanagement.model.enums.UserRole;
import com.ricestoremanagement.repository.UserRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserSeeder implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedEnabled;

    public UserSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.users:true}") boolean seedEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled || userRepository.count() > 0) {
            return;
        }

        userRepository.saveAll(List.of(
                user("admin", "admin123", UserRole.ADMIN),
                user("shipper-1", "shipper123", UserRole.SHIPPER),
                user("shipper-2", "shipper123", UserRole.SHIPPER)));
    }

    private User user(String username, String password, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return user;
    }
}
