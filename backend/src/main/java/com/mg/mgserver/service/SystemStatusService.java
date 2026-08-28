package com.mg.mgserver.service;

import com.mg.mgserver.domain.DispatchTask;
import com.mg.mgserver.domain.TaskStatus;
import com.mg.mgserver.dto.SystemDtos.ServerStatusResponse;
import com.mg.mgserver.dto.SystemDtos.ServerTaskResponse;
import com.mg.mgserver.dto.SettingDtos.AlgorithmSettingResponse;
import com.mg.mgserver.dto.TaskDtos;
import com.mg.mgserver.repository.DispatchTaskRepository;
import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemStatusService {
    public static final int DEFAULT_RUNNING_TASKS = 4;
    public static final int MAX_RUNNING_TASKS = 6;
    private static final Duration CPU_CACHE_TTL = Duration.ofSeconds(2);

    private final DispatchTaskRepository taskRepository;
    private final SettingService settingService;
    private CpuSnapshot cachedCpuSnapshot = new CpuSnapshot(0.0, Instant.EPOCH);

    public SystemStatusService(DispatchTaskRepository taskRepository, SettingService settingService) {
        this.taskRepository = taskRepository;
        this.settingService = settingService;
    }

    @Transactional(readOnly = true)
    public ServerStatusResponse current(Long currentUserId) {
        List<ServerTaskResponse> runningTasks = taskRepository.findByStatusOrderByCreatedAtAsc(TaskStatus.RUNNING).stream()
                .map(task -> toServerTask(task, currentUserId, null))
                .toList();
        List<DispatchTask> queuedTaskOrder = orderedQueuedTasks();
        List<ServerTaskResponse> queuedTasks = new ArrayList<>(queuedTaskOrder.size());
        for (int index = 0; index < queuedTaskOrder.size(); index++) {
            queuedTasks.add(toServerTask(queuedTaskOrder.get(index), currentUserId, queuePriority(index, queuedTaskOrder.size())));
        }
        MemorySnapshot memory = memorySnapshot();
        return new ServerStatusResponse(
                LocalDateTime.now(),
                runningTasks.size(),
                currentMaxRunningTasks(),
                cpuLoadPercent(),
                percent(memory.usedBytes(), memory.totalBytes()),
                toMb(memory.usedBytes()),
                toMb(memory.totalBytes()),
                runningTasks,
                queuedTasks
        );
    }

    public int currentMaxRunningTasks() {
        return DEFAULT_RUNNING_TASKS;
    }

    public Optional<DispatchTask> nextQueuedTask() {
        return orderedQueuedTasks().stream().findFirst();
    }

    public List<DispatchTask> orderedQueuedTasks() {
        List<DispatchTask> queuedTasks = taskRepository.findByStatusOrderByCreatedAtAsc(TaskStatus.QUEUED);
        return queuedTasks.stream()
                .sorted(
                        Comparator.comparingDouble(this::taskPriority).reversed()
                                .thenComparing(DispatchTask::getCreatedAt)
                                .thenComparing(DispatchTask::getId)
                )
                .toList();
    }

    private double taskPriority(DispatchTask task) {
        if (task == null || task.getCreatedAt() == null) {
            return 0.0;
        }
        Duration waiting = Duration.between(task.getCreatedAt(), LocalDateTime.now());
        double waitingHours = Math.max(0.0, waiting.toSeconds() / 3600.0);
        AlgorithmSettingResponse algorithm = settingService.algorithmForTask(task.getId());
        double evaluationCount = algorithm.bee() * (double) algorithm.maxIter() * (1.0 + algorithm.eliteRate() + algorithm.eliminationRate());
        return (waitingHours + 1.0) / (evaluationCount + 1.0);
    }

    private String queuePriority(int index, int total) {
        if (total <= 0) {
            return null;
        }
        int highCount = Math.max(1, (int) Math.round(total * 0.3));
        int mediumCount = Math.max(1, (int) Math.round(total * 0.4));
        if (highCount + mediumCount > total) {
            mediumCount = Math.max(0, total - highCount);
        }
        if (index < highCount) {
            return "HIGH";
        }
        if (index < highCount + mediumCount) {
            return "MEDIUM";
        }
        return "LOW";
    }

    public byte[] desktopScreenshotPng() {
        List<String> errors = new ArrayList<>();
        if (!isWindows()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Desktop screenshot is only supported on Windows");
        }
        try {
            return captureDesktopWithPowerShell();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            errors.add(ex.getMessage());
        }
        throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "桌面截图获取失败，请确认服务器桌面会话处于活动状态后重试");
    }

    private byte[] captureDesktopWithPowerShell() throws IOException, InterruptedException {
        Process process = null;
        CompletableFuture<String> outputFuture = null;
        try {
            String script = """
                    & {
                      $ErrorActionPreference = 'Stop'
                      Add-Type -AssemblyName System.Windows.Forms
                      Add-Type -AssemblyName System.Drawing
                      $bounds = [System.Windows.Forms.SystemInformation]::VirtualScreen
                      $bitmap = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
                      $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
                      $stream = New-Object System.IO.MemoryStream
                      try {
                        $graphics.CopyFromScreen($bounds.Left, $bounds.Top, 0, 0, $bounds.Size)
                        $bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
                        [Convert]::ToBase64String($stream.ToArray())
                      } finally {
                        $graphics.Dispose()
                        $stream.Dispose()
                        $bitmap.Dispose()
                      }
                    }
                    """;
            process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    script
            ).redirectErrorStream(true).start();
            Process runningProcess = process;
            outputFuture = CompletableFuture.supplyAsync(() -> {
                try (var reader = runningProcess.inputReader(Charset.defaultCharset())) {
                    return String.join("\n", reader.lines().toList());
                } catch (IOException ex) {
                    return "";
                }
            });
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("PowerShell screenshot timed out");
            }
            String processOutput = outputFuture.get(2, TimeUnit.SECONDS).trim();
            if (process.exitValue() != 0) {
                throw new IOException("PowerShell screenshot failed" + (processOutput.isBlank() ? "" : ": " + processOutput));
            }
            if (processOutput.isBlank()) {
                throw new IOException("PowerShell screenshot produced an empty image");
            }
            return Base64.getDecoder().decode(processOutput.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException ex) {
            throw new IOException("PowerShell screenshot returned invalid image data", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IOException("PowerShell screenshot output read failed", ex);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private ServerTaskResponse toServerTask(com.mg.mgserver.domain.DispatchTask task, Long currentUserId, String priority) {
        return new ServerTaskResponse(
                task.getId(),
                task.getName(),
                task.getUser().getUsername(),
                task.getStatus(),
                task.getProgress(),
                TaskDtos.estimateRemainingSeconds(task),
                task.getCreatedAt(),
                task.getUser().getId().equals(currentUserId),
                priority
        );
    }

    private double cpuLoadPercent() {
        Double windowsLoad = windowsCpuLoadPercent();
        if (windowsLoad != null) {
            return windowsLoad;
        }
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean osBean) {
            double load = osBean.getSystemCpuLoad();
            if (load < 0) {
                load = osBean.getCpuLoad();
            }
            if (load < 0) {
                load = osBean.getProcessCpuLoad();
            }
            if (load >= 0) {
                return load * 100.0;
            }
        }
        double average = bean.getSystemLoadAverage();
        int processors = Math.max(1, bean.getAvailableProcessors());
        return average >= 0 ? Math.min(100.0, average / processors * 100.0) : 0.0;
    }

    private Double windowsCpuLoadPercent() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("win")) {
            return null;
        }
        Instant now = Instant.now();
        if (Duration.between(cachedCpuSnapshot.sampledAt(), now).compareTo(CPU_CACHE_TTL) < 0) {
            return cachedCpuSnapshot.value();
        }
        synchronized (this) {
            now = Instant.now();
            if (Duration.between(cachedCpuSnapshot.sampledAt(), now).compareTo(CPU_CACHE_TTL) < 0) {
                return cachedCpuSnapshot.value();
            }
            Double sampled = sampleWindowsCpuWithTypeperf();
            if (sampled != null) {
                double bounded = Math.max(0.0, Math.min(100.0, sampled));
                cachedCpuSnapshot = new CpuSnapshot(bounded, Instant.now());
                return bounded;
            }
        }
        return null;
    }

    private Double sampleWindowsCpuWithTypeperf() {
        Process process = null;
        try {
            process = new ProcessBuilder("typeperf", "\\Processor(_Total)\\% Processor Time", "-sc", "2")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS);
            String output = String.join("\n", process.inputReader(Charset.defaultCharset()).lines().toList());
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            return parseTypeperfCpu(output);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Double parseTypeperfCpu(String output) {
        Double latest = null;
        for (String line : output.lines().toList()) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("\"") || !trimmed.contains("\",\"")) {
                continue;
            }
            String[] parts = trimmed.split("\",\"");
            if (parts.length < 2 || parts[0].contains("PDH-CSV")) {
                continue;
            }
            String raw = parts[1].replace("\"", "").trim();
            try {
                latest = Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
                // Ignore non-sample rows from typeperf and keep looking for the latest numeric sample.
            }
        }
        return latest;
    }

    private double percent(long used, long max) {
        return max > 0 ? Math.min(100.0, used * 100.0 / max) : 0.0;
    }

    private long toMb(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0);
    }

    private MemorySnapshot memorySnapshot() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean osBean) {
            long total = osBean.getTotalMemorySize();
            long free = osBean.getFreeMemorySize();
            if (total > 0 && free >= 0) {
                return new MemorySnapshot(total, Math.max(0, total - free));
            }
        }
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return new MemorySnapshot(total, used);
    }

    private record MemorySnapshot(long totalBytes, long usedBytes) {
    }

    private record CpuSnapshot(double value, Instant sampledAt) {
    }
}
