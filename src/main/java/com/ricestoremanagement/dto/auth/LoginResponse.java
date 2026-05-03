package com.ricestoremanagement.dto.auth;

import com.ricestoremanagement.dto.user.UserResponse;

public record LoginResponse(String token, UserResponse user) {
}
