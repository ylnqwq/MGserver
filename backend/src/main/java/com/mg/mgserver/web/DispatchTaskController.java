package com.mg.mgserver.web;

import com.mg.mgserver.domain.UserAccount;
import com.mg.mgserver.dto.TaskDtos.RenameTaskRequest;
import com.mg.mgserver.dto.TaskDtos.TaskDetailResponse;
import com.mg.mgserver.dto.TaskDtos.TaskListResponse;
import com.mg.mgserver.service.AuthService;
import com.mg.mgserver.service.DispatchTaskService;
import com.mg.mgserver.service.ProfileFileService;
import com.mg.mgserver.service.ProfilePoint;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/dispatch")
public class DispatchTaskController {
    private final DispatchTaskService taskService;
    private final ProfileFileService profileFileService;
    private final AuthService authService;

    public DispatchTaskController(DispatchTaskService taskService,
                                  ProfileFileService profileFileService,
                                  AuthService authService) {
        this.taskService = taskService;
        this.profileFileService = profileFileService;
        this.authService = authService;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("microgrid_dispatch_template.csv", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(profileFileService.templateBytes());
    }

    @PostMapping("/tasks")
    public TaskDetailResponse create(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                     @RequestParam(value = "name", required = false) String name,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "settings", required = false) String settingsJson,
                                     @RequestParam(value = "algorithmSettings", required = false) String algorithmSettingsJson) {
        UserAccount user = authService.requireUser(userId);
        return taskService.create(user, name, file, settingsJson, algorithmSettingsJson);
    }

    @PostMapping("/preview")
    public List<ProfilePoint> preview(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                      @RequestParam("file") MultipartFile file) {
        authService.requireUser(userId);
        return profileFileService.parse(file);
    }

    @GetMapping("/tasks")
    public List<TaskListResponse> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        UserAccount user = authService.requireUser(userId);
        return taskService.listForUser(user);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskDetailResponse detail(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                     @PathVariable Long taskId) {
        UserAccount user = authService.requireUser(userId);
        return taskService.detailForUser(user, taskId);
    }

    @PutMapping("/tasks/{taskId}/name")
    public TaskDetailResponse rename(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                     @PathVariable Long taskId,
                                     @RequestBody RenameTaskRequest request) {
        UserAccount user = authService.requireUser(userId);
        return taskService.renameForUser(user, taskId, request.name());
    }

    @PostMapping("/tasks/{taskId}/pause")
    public TaskDetailResponse pause(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @PathVariable Long taskId) {
        UserAccount user = authService.requireUser(userId);
        return taskService.pauseForUser(user, taskId);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public TaskDetailResponse cancel(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                     @PathVariable Long taskId) {
        UserAccount user = authService.requireUser(userId);
        return taskService.cancelForUser(user, taskId);
    }

    @DeleteMapping("/tasks")
    public void delete(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                       @RequestParam("ids") List<Long> taskIds) {
        UserAccount user = authService.requireUser(userId);
        taskService.deleteForUser(user, taskIds);
    }

    @GetMapping("/tasks/{taskId}/download/{filename}")
    public ResponseEntity<byte[]> download(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                           @PathVariable Long taskId,
                                           @PathVariable String filename) throws Exception {
        UserAccount user = authService.requireUser(userId);
        Path path = taskService.resultFileForUser(user, taskId, filename);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(resolveDownloadMediaType(filename))
                .body(Files.readAllBytes(path));
    }

    private MediaType resolveDownloadMediaType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (lower.endsWith(".json")) {
            return new MediaType("application", "json", StandardCharsets.UTF_8);
        }
        return new MediaType("text", "csv", StandardCharsets.UTF_8);
    }

    @GetMapping("/admin/tasks")
    public List<TaskListResponse> adminList(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        authService.requireAdmin(userId);
        return taskService.listAll();
    }
}
