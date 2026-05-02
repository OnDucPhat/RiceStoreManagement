package com.ricestoremanagement.repository;

import com.ricestoremanagement.model.User;
import com.ricestoremanagement.model.enums.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    List<User> findByRoleOrderByUsernameAsc(UserRole role);
}
