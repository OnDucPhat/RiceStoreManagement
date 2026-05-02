package com.ricestoremanagement.controller;

import com.ricestoremanagement.dto.user.UserResponse;
import com.ricestoremanagement.model.User;
import com.ricestoremanagement.model.enums.UserRole;
import com.ricestoremanagement.service.UserService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> getUsers(@RequestParam(name = "role", required = false) UserRole role) {
        List<User> users = userService.getUsers(role);
        return users.stream().map(UserResponse::from).collect(Collectors.toList());
    }
}
