package com.mg.mgserver.service;

import com.mg.mgserver.domain.UserAccount;
import com.mg.mgserver.domain.UserRole;
import com.mg.mgserver.dto.AdminDtos.AdminUserResponse;
import com.mg.mgserver.dto.AdminDtos.UpdateUserRoleRequest;
import com.mg.mgserver.dto.AuthDtos.LoginRequest;
import com.mg.mgserver.dto.AuthDtos.RegisterRequest;
import com.mg.mgserver.dto.AuthDtos.UserResponse;
import com.mg.mgserver.repository.DispatchTaskRepository;
import com.mg.mgserver.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String ACTIVE_STATUS = "ACTIVE";
    private final UserAccountRepository userRepository;
    private final DispatchTaskRepository taskRepository;

    public AuthService(UserAccountRepository userRepository, DispatchTaskRepository taskRepository) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new AppException(HttpStatus.CONFLICT, "用户名已存在");
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(hash(request.password()));
        user.setRole(request.role() == null ? UserRole.USER : request.role());
        user.setStatus(ACTIVE_STATUS);
        userRepository.save(user);
        return toResponse(user);
    }

    public UserResponse login(LoginRequest request) {
        UserAccount user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!user.getPasswordHash().equals(hash(request.password()))) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (request.role() != null && user.getRole() != request.role()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "账号身份不匹配");
        }
        return toResponse(user);
    }

    public UserAccount requireUser(Long id) {
        if (id == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }

    public UserAccount requireAdmin(Long id) {
        UserAccount user = requireUser(id);
        if (user.getRole() != UserRole.ADMIN) {
            throw new AppException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        return user;
    }

    public UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }

    public List<AdminUserResponse> listUsersForAdmin(Long adminId) {
        requireAdmin(adminId);
        return userRepository.findAll().stream()
                .map(this::toAdminUserResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long adminId, Long targetUserId, UpdateUserRoleRequest request) {
        UserAccount admin = requireAdmin(adminId);
        UserRole nextRole = request.role();
        if (nextRole == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "请选择用户权限");
        }
        UserAccount target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (admin.getId().equals(target.getId()) && nextRole != UserRole.ADMIN) {
            long adminCount = userRepository.findAll().stream()
                    .filter(user -> user.getRole() == UserRole.ADMIN)
                    .count();
            if (adminCount <= 1) {
                throw new AppException(HttpStatus.BAD_REQUEST, "至少保留一个管理员账号");
            }
        }
        target.setRole(nextRole);
        return toAdminUserResponse(target);
    }

    private AdminUserResponse toAdminUserResponse(UserAccount user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                taskRepository.countByUser(user),
                user.getCreatedAt()
        );
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
