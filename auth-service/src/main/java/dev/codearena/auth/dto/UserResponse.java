package dev.codearena.auth.dto;

import dev.codearena.auth.domain.User;

public record UserResponse(
        String id,
        String email,
        String username,
        String role,
        String createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().toHexString(),
                user.getEmail(),
                user.getUsername(),
                user.getRole().name(),
                user.getCreatedAt().toString()
        );
    }
}
