package com.ricestoremanagement.service;

import com.ricestoremanagement.model.User;
import com.ricestoremanagement.model.enums.UserRole;
import com.ricestoremanagement.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public List<User> getUsers(UserRole role) {
        if (role == null) {
            return userRepository.findAll(Sort.by(Sort.Direction.ASC, "username"));
        }
        return userRepository.findByRoleOrderByUsernameAsc(role);
    }
}
