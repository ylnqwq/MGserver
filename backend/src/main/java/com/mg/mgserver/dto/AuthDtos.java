package com.mg.mgserver.dto;

import com.mg.mgserver.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            UserRole role
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            UserRole role
    ) {
    }

    public record UserResponse(Long id, String username, UserRole role) {
    }
}
