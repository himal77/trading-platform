package com.tradingplatform.auth.api.dto;

import com.tradingplatform.auth.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String tier,
        String status,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getTier().name(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
