package com.mg.mgserver.web;

import com.mg.mgserver.dto.AdminDtos.AdminUserResponse;
import com.mg.mgserver.dto.AdminDtos.UpdateUserRoleRequest;
import com.mg.mgserver.service.AuthService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return authService.listUsersForAdmin(userId);
    }

    @PutMapping("/users/{targetUserId}/role")
    public AdminUserResponse updateRole(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                        @PathVariable Long targetUserId,
                                        @RequestBody UpdateUserRoleRequest request) {
        return authService.updateUserRole(userId, targetUserId, request);
    }
}
