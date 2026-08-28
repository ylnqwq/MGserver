package com.mg.mgserver.web;

import com.mg.mgserver.dto.SettingDtos.SettingRequest;
import com.mg.mgserver.dto.SettingDtos.SettingResponse;
import com.mg.mgserver.dto.SettingDtos.AlgorithmSettingRequest;
import com.mg.mgserver.dto.SettingDtos.AlgorithmSettingResponse;
import com.mg.mgserver.service.AuthService;
import com.mg.mgserver.service.SettingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingController {
    private final SettingService settingService;
    private final AuthService authService;

    public SettingController(SettingService settingService, AuthService authService) {
        this.settingService = settingService;
        this.authService = authService;
    }

    @GetMapping
    public SettingResponse current() {
        return SettingResponse.from(settingService.getOrCreate());
    }

    @PutMapping
    public SettingResponse update(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                  @Valid @RequestBody SettingRequest request) {
        authService.requireAdmin(userId);
        return settingService.update(request);
    }

    @GetMapping("/algorithm")
    public AlgorithmSettingResponse algorithm(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        authService.requireUser(userId);
        return settingService.algorithm();
    }

    @PutMapping("/algorithm")
    public AlgorithmSettingResponse updateAlgorithm(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                    @Valid @RequestBody AlgorithmSettingRequest request) {
        authService.requireAdmin(userId);
        return settingService.updateAlgorithm(request);
    }
}
