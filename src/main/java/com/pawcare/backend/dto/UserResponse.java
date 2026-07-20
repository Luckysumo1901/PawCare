package com.pawcare.backend.dto;

import java.time.Instant;
import java.util.List;

import com.pawcare.backend.entity.User;

public record UserResponse(
        String id, String name, String email, List<String> roles, Instant createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRoles(), u.getCreatedAt());
    }
}