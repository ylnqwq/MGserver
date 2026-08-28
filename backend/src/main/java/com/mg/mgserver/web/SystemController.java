package com.mg.mgserver.web;

import com.mg.mgserver.dto.SystemDtos.ServerStatusResponse;
import com.mg.mgserver.service.AuthService;
import com.mg.mgserver.service.SystemStatusService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final AuthService authService;
    private final SystemStatusService systemStatusService;

    public SystemController(AuthService authService, SystemStatusService systemStatusService) {
        this.authService = authService;
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/status")
    public ServerStatusResponse status(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        authService.requireUser(userId);
        return systemStatusService.current(userId);
    }

    @GetMapping(value = "/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] screenshot(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        authService.requireUser(userId);
        return systemStatusService.desktopScreenshotPng();
    }
}
