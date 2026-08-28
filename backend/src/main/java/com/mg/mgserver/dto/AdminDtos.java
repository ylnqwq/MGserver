package com.mg.mgserver.dto;

import com.mg.mgserver.domain.UserRole;
import java.time.LocalDateTime;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record AdminUserResponse(
            Long id,
            String username,
            String passwordHash,
            UserRole role,
            long taskCount,
            LocalDateTime createdAt
    ) {
    }

    public record UpdateUserRoleRequest(UserRole role) {
    }
}
