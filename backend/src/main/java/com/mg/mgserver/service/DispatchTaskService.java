package com.mg.mgserver.service;

import com.mg.mgserver.domain.DeviceParam;
import com.mg.mgserver.domain.DispatchTask;
import com.mg.mgserver.domain.ForecastFile;
import com.mg.mgserver.domain.ResultRecord;
import com.mg.mgserver.domain.TaskStatus;
import com.mg.mgserver.domain.UserAccount;
import com.mg.mgserver.dto.SettingDtos.AlgorithmSettingRequest;
import com.mg.mgserver.dto.SettingDtos.AlgorithmSettingResponse;
import com.mg.mgserver.dto.SettingDtos.SettingRequest;
import com.mg.mgserver.dto.SettingDtos.SettingResponse;
import com.mg.mgserver.dto.TaskDtos;
import com.mg.mgserver.dto.TaskDtos.TaskDetailResponse;
import com.mg.mgserver.dto.TaskDtos.TaskListResponse;
import com.mg.mgserver.repository.DeviceParamRepository;
import com.mg.mgserver.repository.DispatchTaskRepository;
import com.mg.mgserver.repository.ForecastFileRepository;
import com.mg.mgserver.repository.ResultRecordRepository;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class DispatchTaskService {
    private static final Logger log = LoggerFactory.getLogger(DispatchTaskService.class);
    private static final long DEFAULT_ROW_ID = 0L;
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("MG_PROGRESS=([0-9]+)(?:/([0-9]+))?");
    private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> BUNDLED_ALGORITHM_RESOURCES = List.of(
            "microgrid_business.py",
            "input_template.csv",
            "README.md",
            "multi_objective/__init__.py",
            "multi_objective/mo_utils.py",
            "multi_objective/algorithms/__init__.py",
            "multi_objective/algorithms/MOIABC.py",
            "multi_objective/application_point/__init__.py",
            "multi_objective/application_point/microgrid_dispatch_model.py"
    );

    private final DispatchTaskRepository taskRepository;
    private final DeviceParamRepository deviceParamRepository;
    private final ForecastFileRepository forecastFileRepository;
    private final ResultRecordRepository resultRecordRepository;
    private final ProfileFileService profileFileService;
    private final SettingService settingService;
    private final SystemStatusService systemStatusService;
    private final ObjectMapper objectMapper;
    private final String pythonCommand;
    private final String configuredAlgorithmDir;
    private final int bee;
    private final int maxIter;
    private final int archiveSize;
    private final JdbcTemplate jdbcTemplate;
    private final Map<Long, Process> activeProcesses = new ConcurrentHashMap<>();

    public DispatchTaskService(DispatchTaskRepository taskRepository,
                               DeviceParamRepository deviceParamRepository,
                               ForecastFileRepository forecastFileRepository,
                               ResultRecordRepository resultRecordRepository,
                               ProfileFileService profileFileService,
                               SettingService settingService,
                               SystemStatusService systemStatusService,
                               ObjectMapper objectMapper,
                               @Value("${mg.algorithm.python:python}") String pythonCommand,
                               @Value("${mg.algorithm.dir:}") String configuredAlgorithmDir,
                               @Value("${mg.algorithm.bee:60}") int bee,
                               @Value("${mg.algorithm.max-iter:300}") int maxIter,
                               @Value("${mg.algorithm.archive-size:80}") int archiveSize,
                               JdbcTemplate jdbcTemplate) {
        this.taskRepository = taskRepository;
        this.deviceParamRepository = deviceParamRepository;
        this.forecastFileRepository = forecastFileRepository;
        this.resultRecordRepository = resultRecordRepository;
        this.profileFileService = profileFileService;
        this.settingService = settingService;
        this.systemStatusService = systemStatusService;
        this.objectMapper = objectMapper;
        this.pythonCommand = pythonCommand;
        this.configuredAlgorithmDir = configuredAlgorithmDir;
        this.bee = bee;
        this.maxIter = maxIter;
        this.archiveSize = archiveSize;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public synchronized TaskDetailResponse create(UserAccount user, String name, MultipartFile file, String settingsJson, String algorithmSettingsJson) {
        profileFileService.parse(file);
        JsonNode effectiveSettings = resolveEffectiveSettings(settingsJson);
        AlgorithmSettingRequest effectiveAlgorithmSettings = resolveEffectiveAlgorithmSettings(algorithmSettingsJson);

        DispatchTask task = new DispatchTask();
        task.setUser(user);
        task.setName(name == null || name.isBlank() ? "Daily dispatch task" : name.trim());
        task.setStatus(TaskStatus.QUEUED);
        task.setProgress(0);
        task.setMessage("Task queued");
        task.setStartTime(null);
        taskRepository.save(task);

        DeviceParam param = buildDeviceParam(task, effectiveSettings);
        deviceParamRepository.save(param);

        if (effectiveAlgorithmSettings != null) {
            settingService.saveTaskAlgorithm(task, effectiveAlgorithmSettings);
        }

        ForecastFile forecastFile = buildForecastFile(task, file);
        forecastFileRepository.save(forecastFile);

        persistUpload(task.getId(), file);
        persistSettings(task.getId(), effectiveSettings);
        afterCommit(this::refillDispatchPool);
        return toDetail(task);
    }

    public List<TaskListResponse> listForUser(UserAccount user) {
        return taskRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toListResponse)
                .toList();
    }

    public List<TaskListResponse> listAll() {
        return taskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toListResponse)
                .toList();
    }

    public TaskDetailResponse detailForUser(UserAccount user, Long taskId) {
        DispatchTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!task.getUser().getId().equals(user.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only own tasks can be viewed");
        }
        return toDetail(task);
    }

    @Transactional
    public TaskDetailResponse renameForUser(UserAccount user, Long taskId, String name) {
        DispatchTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!task.getUser().getId().equals(user.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only own tasks can be renamed");
        }
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Task name cannot be blank");
        }
        if (normalized.length() > 120) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Task name cannot exceed 120 characters");
        }
        task.setName(normalized);
        taskRepository.save(task);
        return toDetail(task);
    }

    @Transactional
    public TaskDetailResponse cancelForUser(UserAccount user, Long taskId) {
        DispatchTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!task.getUser().getId().equals(user.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only own tasks can be canceled");
        }
        if (task.getStatus() == TaskStatus.CANCELED || task.getStatus() == TaskStatus.PAUSED) {
            return toDetail(task);
        }
        if (task.getStatus() != TaskStatus.RUNNING && task.getStatus() != TaskStatus.QUEUED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only running or queued tasks can be canceled");
        }
        boolean wasRunning = task.getStatus() == TaskStatus.RUNNING;
        boolean needsRefill = task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.QUEUED;
        task.setStatus(TaskStatus.CANCELED);
        task.setMessage(wasRunning ? "Task canceled; process stopped" : "Task canceled; removed from queue");
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);
        Process process = activeProcesses.remove(taskId);
        afterCommit(() -> {
            if (process != null) {
                stopProcess(process);
            }
            if (needsRefill) {
                refillDispatchPool();
            }
        });
        return toDetail(task);
    }

    @Transactional
    public TaskDetailResponse pauseForUser(UserAccount user, Long taskId) {
        return cancelForUser(user, taskId);
    }

    private void afterCommit(Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (Exception ex) {
                log.warn("After-commit action failed", ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            guarded.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                guarded.run();
            }
        });
    }

    @Transactional
    public void deleteForUser(UserAccount user, List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Please select tasks to delete");
        }
        List<Long> uniqueIds = taskIds.stream().distinct().toList();
        for (Long taskId : uniqueIds) {
            DispatchTask task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Task not found"));
            if (!task.getUser().getId().equals(user.getId())) {
                throw new AppException(HttpStatus.FORBIDDEN, "Only own tasks can be deleted");
            }
            boolean wasRunning = task.getStatus() == TaskStatus.RUNNING;
            boolean needsRefill = wasRunning || task.getStatus() == TaskStatus.QUEUED;
            Process process = activeProcesses.remove(taskId);
            resultRecordRepository.deleteByTask_Id(taskId);
            forecastFileRepository.deleteByTask_Id(taskId);
            deviceParamRepository.findFirstByTask_IdOrderByCreatedAtDesc(taskId).ifPresent(deviceParamRepository::delete);
            taskRepository.delete(task);
            deleteRunDirectory(taskId);
            afterCommit(() -> {
                if (process != null) {
                    stopProcess(process);
                }
                if (needsRefill) {
                    refillDispatchPool();
                }
            });
        }
    }

    public Path resultFileForUser(UserAccount user, Long taskId, String filename) {
        DispatchTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!task.getUser().getId().equals(user.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only own tasks can be downloaded");
        }
        List<String> allowed = List.of(
                "dispatch_curves.csv",
                "dispatch_curves_balanced.csv",
                "dispatch_curves_economic_min.csv",
                "dispatch_curves_environment_min.csv",
                "pareto_front.csv",
                "convergence.csv",
                "report.pdf"
        );
        if (!allowed.contains(filename)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Unsupported result file");
        }
        Path path = runsDir().resolve("task-" + taskId).resolve("output").resolve(filename).normalize();
        if ("report.pdf".equals(filename) && task.getStatus() == TaskStatus.COMPLETED) {
            ensureTaskReport(task, path);
        }
        if (!Files.exists(path)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Result file not found");
        }
        return path;
    }

    private void startTaskAsync(Long taskId, Path inputPath, Path settingsPath) {
        CompletableFuture.runAsync(() -> runAlgorithm(taskId, inputPath, settingsPath));
    }

    private void runAlgorithm(Long taskId, Path inputPath, Path settingsPath) {
        Path outputDir = runsDir().resolve("task-" + taskId).resolve("output");
        Process process = null;
        try {
            Files.createDirectories(outputDir);
            updateProgress(taskId, 5, "Algorithm started");
            Path algorithmDir = resolveAlgorithmDirectory();
            Path scriptPath = algorithmDir.resolve("microgrid_business.py").normalize();
            AlgorithmSettingResponse algorithmSetting = settingService.algorithmForTask(taskId);
            List<String> command = new ArrayList<>();
            command.add(pythonCommand);
            command.add(scriptPath.toString());
            command.add("--input");
            command.add(inputPath.toString());
            command.add("--output");
            command.add(outputDir.toString());
            command.add("--bee");
            command.add(String.valueOf(algorithmSetting.bee()));
            command.add("--max-iter");
            command.add(String.valueOf(algorithmSetting.maxIter()));
            command.add("--limit");
            command.add(String.valueOf(algorithmSetting.limit()));
            command.add("--archive-size");
            command.add(String.valueOf(algorithmSetting.archiveSize()));
            command.add("--tournament-size");
            command.add(String.valueOf(algorithmSetting.tournamentSize()));
            command.add("--elite-rate");
            command.add(String.valueOf(algorithmSetting.eliteRate()));
            command.add("--elimination-rate");
            command.add(String.valueOf(algorithmSetting.eliminationRate()));
            command.add("--archive-guidance-rate");
            command.add(String.valueOf(algorithmSetting.archiveGuidanceRate()));
            command.add("--params");
            command.add(settingsPath.toString());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(algorithmDir.toFile());
            builder.redirectErrorStream(true);
            process = builder.start();
            activeProcesses.put(taskId, process);
            StringBuilder logs = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.append(line).append(System.lineSeparator());
                    parseProgress(line).ifPresent(progress -> updateProgress(taskId, progress, "Algorithm running"));
                }
            }
            int exitCode = process.waitFor();
            if (isPaused(taskId)) {
                return;
            }
            if (exitCode != 0) {
                failTask(taskId, "Algorithm failed: " + truncate(logs.toString()));
                return;
            }
            completeTask(taskId, outputDir);
        } catch (Exception ex) {
            if (isPaused(taskId)) {
                return;
            }
            failTask(taskId, "Algorithm failed: " + truncate(ex.getMessage()));
        } finally {
            activeProcesses.remove(taskId, process);
        }
    }

    private void stopProcess(Process process) {
        process.destroy();
        CompletableFuture.runAsync(() -> {
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        });
    }

    private boolean isPaused(Long taskId) {
        return taskRepository.findById(taskId)
                .map(task -> task.getStatus() == TaskStatus.PAUSED || task.getStatus() == TaskStatus.CANCELED)
                .orElse(false);
    }

    private void completeTask(Long taskId, Path outputDir) throws IOException {
        DispatchTask task = taskRepository.findById(taskId).orElseThrow();
        JsonNode rawSummary = objectMapper.readTree(outputDir.resolve("summary.json").toFile());
        JsonNode curves = objectMapper.readTree(outputDir.resolve("dispatch_curves.json").toFile());
        JsonNode solutionOptions = readOptionalOutputJson(outputDir.resolve("solution_options.json"), "[]");
        ObjectNode summary = normalizeSummary(rawSummary);

        ResultRecord result = new ResultRecord();
        result.setTask(task);
        result.setEconomicCost(summary.path("economicCost").asDouble(0.0));
        result.setEnvironmentCost(summary.path("environmentCost").asDouble(0.0));
        result.setRenewableUtilizationRate(summary.path("renewableUtilizationRate").asDouble(0.0));
        result.setFinalSoc(summary.path("finalSoc").asDouble(0.0));
        result.setCurrentScheme(summary.path("currentScheme").asText("balanced"));
        result.setParetoPath(outputDir.resolve("pareto_front.csv").toString());
        result.setResultPath(outputDir.toString());
        resultRecordRepository.save(result);

        task.setStatus(TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setMessage("Algorithm completed");
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);
        ensureTaskReport(task, outputDir.resolve("report.pdf"));
        refillDispatchPool();
    }

    private ObjectNode normalizeSummary(JsonNode rawSummary) {
        ObjectNode summary = rawSummary.isObject() ? (ObjectNode) rawSummary.deepCopy() : objectMapper.createObjectNode();
        summary.put("economicCost", rawSummary.path("compromise_economic_cost").asDouble(rawSummary.path("economicCost").asDouble(0.0)));
        summary.put("environmentCost", rawSummary.path("compromise_environment_cost").asDouble(rawSummary.path("environmentCost").asDouble(0.0)));
        summary.put("renewableUtilizationRate", rawSummary.path("renewable_utilization_rate").asDouble(rawSummary.path("renewableUtilizationRate").asDouble(0.0)));
        summary.put("finalSoc", rawSummary.path("final_soc").asDouble(rawSummary.path("finalSoc").asDouble(0.0)));
        return summary;
    }

    private JsonNode resolveEffectiveSettings(String settingsJson) {
        try {
            if (settingsJson == null || settingsJson.isBlank()) {
                return objectMapper.valueToTree(SettingResponse.from(settingService.getOrCreate()));
            }
            SettingRequest request = objectMapper.readValue(settingsJson, SettingRequest.class);
            settingService.validateRange(request);
            return objectMapper.valueToTree(request);
        } catch (JacksonException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "鐠佹儳顦崣鍌涙殶閺嶇厧绱￠柨娆掝嚖");
        }
    }

    private AlgorithmSettingRequest resolveEffectiveAlgorithmSettings(String algorithmSettingsJson) {
        try {
            if (algorithmSettingsJson == null || algorithmSettingsJson.isBlank()) {
                return null;
            }
            AlgorithmSettingRequest request = objectMapper.readValue(algorithmSettingsJson, AlgorithmSettingRequest.class);
            settingService.validateAlgorithmRange(request);
            return request;
        } catch (JacksonException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid MOIABC settings");
        }
    }

    private JsonNode readOptionalOutputJson(Path path, String fallbackJson) throws IOException {
        if (Files.exists(path)) {
            return objectMapper.readTree(path.toFile());
        }
        return objectMapper.readTree(fallbackJson);
    }

    private Optional<Integer> parseProgress(String line) {
        Matcher matcher = PROGRESS_PATTERN.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int value = Integer.parseInt(matcher.group(1));
        String totalText = matcher.group(2);
        if (totalText == null) {
            return Optional.of(Math.max(0, Math.min(99, value)));
        }
        int total = Integer.parseInt(totalText);
        if (total <= 0) {
            return Optional.empty();
        }
        int percent = (int) Math.floor(value * 100.0 / total);
        return Optional.of(Math.max(0, Math.min(99, percent)));
    }

    private void updateProgress(Long taskId, int progress, String message) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == TaskStatus.RUNNING) {
                task.setProgress(progress);
                task.setMessage(message);
                taskRepository.save(task);
            }
        });
    }

    private void failTask(Long taskId, String message) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(TaskStatus.FAILED);
            task.setProgress(100);
            task.setMessage(message);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
        refillDispatchPool();
    }

    private synchronized void refillDispatchPool() {
        while (taskRepository.countByStatus(TaskStatus.RUNNING) < systemStatusService.currentMaxRunningTasks()) {
            DispatchTask task = claimNextQueuedTask();
            if (task == null) {
                return;
            }
            startTaskAsync(task.getId(), inputPathForTask(task.getId()), runsDir().resolve("task-" + task.getId()).resolve("settings.json"));
        }
    }

    private DispatchTask claimNextQueuedTask() {
        LocalDateTime startedAt = LocalDateTime.now();
        for (DispatchTask candidate : systemStatusService.orderedQueuedTasks()) {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE dispatch_task
                       SET status = ?,
                           progress = 0,
                           start_time = COALESCE(start_time, ?),
                           message = ?
                     WHERE id = ?
                       AND status = ?
                    """,
                    TaskStatus.RUNNING.name(),
                    Timestamp.valueOf(startedAt),
                    "Task admitted to running pool",
                    candidate.getId(),
                    TaskStatus.QUEUED.name()
            );
            if (updated > 0) {
                return taskRepository.findById(candidate.getId()).orElse(null);
            }
        }
        return null;
    }

    private void ensureTaskReport(DispatchTask task, Path reportPath) {
        ResultRecord result = resultRecordRepository.findFirstByTask_IdOrderByCreatedAtDesc(task.getId()).orElse(null);
        if (result == null) {
            return;
        }
        try {
            Files.createDirectories(reportPath.getParent());
            writeTaskReport(task, result, reportPath);
        } catch (IOException ex) {
            log.warn("Failed to write task report for task {}", task.getId(), ex);
        }
    }

    private void writeTaskReport(DispatchTask task, ResultRecord result, Path reportPath) throws IOException {
        List<BufferedImage> pages = renderExpandedTaskReportPages(task, result);
        try (PDDocument document = new PDDocument()) {
            for (BufferedImage image : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                var pdfImage = LosslessFactory.createFromImage(document, image);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    float pageWidth = page.getMediaBox().getWidth();
                    float pageHeight = page.getMediaBox().getHeight();
                    float margin = 18f;
                    contentStream.drawImage(pdfImage, margin, margin, pageWidth - margin * 2, pageHeight - margin * 2);
                }
            }
            document.save(reportPath.toFile());
        }
    }

    private BufferedImage renderTaskReportImage(DispatchTask task, ResultRecord result) {
        return new DispatchReportGenerator().renderPages(buildExpandedReportData(task, result)).get(0);
    }

    private List<BufferedImage> renderExpandedTaskReportPages(DispatchTask task, ResultRecord result) {
        return new DispatchReportGenerator().renderPages(buildExpandedReportData(task, result));
    }
    private ExpandedReportData buildExpandedReportData(DispatchTask task, ResultRecord result) {
        JsonNode summary = readTaskOutputJson(task, "summary.json", "{}");
        List<ProfilePoint> profiles = readExpandedForecastPoints(task);
        List<DispatchCurvePoint> curves = parseExpandedDispatchCurves(readTaskOutputJson(task, "dispatch_curves.json", "[]"));
        List<ExpandedReportOption> options = parseExpandedReportOptions(readTaskOutputJson(task, "solution_options.json", "[]"), 8);
        List<ExpandedReportOption> pareto = parseExpandedReportOptions(readTaskOutputJson(task, "pareto_front.json", "[]"), 8);
        DeviceParam param = deviceParamRepository.findFirstByTask_IdOrderByCreatedAtDesc(task.getId())
                .orElseGet(this::getCurrentDeviceParam);
        AlgorithmSettingResponse algorithmSetting = settingService.algorithmForTask(task.getId());
        return new ExpandedReportData(task, result, SettingResponse.from(param), algorithmSetting, summary, profiles, curves, options, pareto);
    }

    private List<ProfilePoint> readExpandedForecastPoints(DispatchTask task) {
        ForecastFile forecastFile = forecastFileRepository.findFirstByTask_IdOrderByUploadTimeDesc(task.getId()).orElse(null);
        if (forecastFile == null || forecastFile.getFilePath() == null) {
            return List.of();
        }
        Path path = Path.of(forecastFile.getFilePath());
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return profileFileService.parse(path, forecastFile.getFileName());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<List<String>> buildExpandedOptionRows(List<ExpandedReportOption> options) {
        if (options.isEmpty()) {
            return List.of(List.of("-", "-", "-", "-", "-", "-", "-"));
        }
        List<List<String>> rows = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            ExpandedReportOption option = options.get(index);
            rows.add(List.of(
                    option.displayName(index),
                    truncate(option.description()),
                    formatDouble(option.economicCost()),
                    formatDouble(option.environmentCost()),
                    formatDouble(option.penalty()),
                    formatPercent(option.finalSoc()),
                    formatPercent(option.renewableUtilizationRate())
            ));
        }
        return rows;
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private List<DispatchCurvePoint> parseExpandedDispatchCurves(JsonNode node) {
        List<DispatchCurvePoint> curves = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return curves;
        }
        for (JsonNode item : node) {
            curves.add(new DispatchCurvePoint(
                    item.path("period").asInt(item.path("hour").asInt(curves.size() + 1)),
                    item.path("buy_price").asDouble(item.path("buyPrice").asDouble(0.0)),
                    item.path("sell_price").asDouble(item.path("sellPrice").asDouble(0.0)),
                    item.path("load_kw").asDouble(item.path("loadKw").asDouble(0.0)),
                    item.path("pv_kw").asDouble(item.path("pvKw").asDouble(0.0)),
                    item.path("wind_kw").asDouble(item.path("wtKw").asDouble(0.0)),
                    item.path("diesel_kw").asDouble(item.path("microTurbineKw").asDouble(0.0)),
                    item.path("battery_kw").asDouble(item.path("batteryKw").asDouble(0.0)),
                    item.path("grid_buy_kw").asDouble(item.path("gridBuyKw").asDouble(0.0)),
                    item.path("grid_sell_kw").asDouble(item.path("gridSellKw").asDouble(0.0)),
                    item.path("soc").asDouble(0.0),
                    item.path("total_curtail_kw").asDouble(item.path("curtailedKw").asDouble(0.0))
            ));
        }
        return curves;
    }

    private List<ExpandedReportOption> parseExpandedReportOptions(JsonNode node, int limit) {
        List<ExpandedReportOption> options = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return options;
        }
        for (JsonNode item : node) {
            if (options.size() >= limit) {
                break;
            }
            options.add(new ExpandedReportOption(
                    item.path("key").asText(item.path("label").asText("-")),
                    item.path("label").asText(item.path("key").asText("-")),
                    item.path("description").asText("-"),
                    item.path("index").asInt(item.path("paretoIndex").asInt(0)),
                    item.path("isCompromise").asBoolean(item.path("is_compromise").asBoolean(false)),
                    item.path("isEconomicBest").asBoolean(item.path("is_economic_best").asBoolean(false)),
                    item.path("isEnvironmentBest").asBoolean(item.path("is_environment_best").asBoolean(false)),
                    item.path("economicCost").asDouble(item.path("economic_cost").asDouble(0.0)),
                    item.path("environmentCost").asDouble(item.path("environment_cost").asDouble(0.0)),
                    item.path("penalty").asDouble(0.0),
                    item.path("finalSoc").asDouble(0.0),
                    item.path("renewableUtilizationRate").asDouble(item.path("renewable_utilization_rate").asDouble(0.0))
            ));
        }
        return options;
    }

    record ExpandedReportData(
            DispatchTask task,
            ResultRecord result,
            SettingResponse deviceSetting,
            AlgorithmSettingResponse algorithmSetting,
            JsonNode summary,
            List<ProfilePoint> profiles,
            List<DispatchCurvePoint> dispatchCurves,
            List<ExpandedReportOption> solutionOptions,
            List<ExpandedReportOption> paretoFront
    ) {
    }

    private record ExpandedReportCard(String label, String value, String note, Color accent) {
    }

    private record ExpandedReportField(String label, String value) {
    }

    private record ExpandedChartSeries(String label, Color color, List<Double> values) {
    }

    record ExpandedReportOption(
            String key,
            String label,
            String description,
            int index,
            boolean compromise,
            boolean economicBest,
            boolean environmentBest,
            double economicCost,
            double environmentCost,
            double penalty,
            double finalSoc,
            double renewableUtilizationRate
    ) {
        private String displayName(int fallbackIndex) {
            if (label != null && !label.isBlank()) {
                return label;
            }
            if (key != null && !key.isBlank()) {
                return key;
            }
            return index > 0 ? String.valueOf(index) : String.valueOf(fallbackIndex + 1);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void refillRunningSlots() {
        refillDispatchPool();
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void failHistoricalTasksOnStartup() {
        terminateOrphanedAlgorithmProcesses();
        failTasksByStatus(TaskStatus.RUNNING, "系统启动时未完成的任务已失败");
        failTasksByStatus(TaskStatus.QUEUED, "系统启动时未完成的任务已失败");
        activeProcesses.clear();
    }

    @Transactional
    @EventListener(ContextClosedEvent.class)
    public void failActiveTasksOnShutdown() {
        activeProcesses.values().forEach(this::stopProcess);
        activeProcesses.clear();
        terminateOrphanedAlgorithmProcesses();
        failTasksByStatus(TaskStatus.RUNNING, "系统关闭时未完成的任务已失败");
        failTasksByStatus(TaskStatus.QUEUED, "系统关闭时未完成的任务已失败");
    }

    private void failTasksByStatus(TaskStatus status, String message) {
        taskRepository.findByStatusOrderByCreatedAtAsc(status).forEach(task -> {
            task.setStatus(TaskStatus.FAILED);
            task.setProgress(100);
            task.setMessage(message);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void terminateOrphanedAlgorithmProcesses() {
        if (!isWindows()) {
            return;
        }
        Process process = null;
        try {
            String script = """
                    Get-CimInstance Win32_Process |
                      Where-Object {
                        $_.CommandLine -and ($_.CommandLine -match 'microgrid_business\\.py')
                      } |
                      ForEach-Object {
                        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
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
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Path persistUpload(Long taskId, MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "input.csv" : file.getOriginalFilename();
        String extension = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            extension = original.substring(dot).toLowerCase(Locale.ROOT);
        }
        Path inputPath = runsDir().resolve("task-" + taskId).resolve("input" + extension);
        try {
            Files.createDirectories(inputPath.getParent());
            Files.copy(file.getInputStream(), inputPath, StandardCopyOption.REPLACE_EXISTING);
            return inputPath;
        } catch (IOException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save uploaded file");
        }
    }

    private Path persistSettings(Long taskId, JsonNode settings) {
        Path settingsPath = runsDir().resolve("task-" + taskId).resolve("settings.json");
        try {
            Files.createDirectories(settingsPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), settings);
            return settingsPath;
        } catch (IOException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save task settings");
        }
    }

    private void deleteRunDirectory(Long taskId) {
        Path root = runsDir();
        Path target = root.resolve("task-" + taskId).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            });
        } catch (IOException ignored) {
            // Leave orphaned files if the OS has them locked.
        }
    }

    private Path inputPathForTask(Long taskId) {
        return forecastFileRepository.findFirstByTask_IdOrderByUploadTimeDesc(taskId)
                .map(file -> Path.of(file.getFilePath()))
                .orElseGet(() -> {
                    Path taskDir = runsDir().resolve("task-" + taskId).normalize();
                    try (var paths = Files.list(taskDir)) {
                        return paths
                                .filter(path -> path.getFileName().toString().startsWith("input."))
                                .findFirst()
                                .orElseThrow(() -> new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Task input file not found"));
                    } catch (IOException ex) {
                        throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Task input file not found");
                    }
                });
    }

    private Path resolveAlgorithmDirectory() throws IOException {
        if (configuredAlgorithmDir != null && !configuredAlgorithmDir.isBlank()) {
            Path configured = Path.of(configuredAlgorithmDir).toAbsolutePath().normalize();
            if (!Files.exists(configured.resolve("microgrid_business.py"))) {
                throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Algorithm script not found: " + configured.resolve("microgrid_business.py"));
            }
            return configured;
        }

        Path root = projectRoot();
        Path external = root.resolve("MOIABC").normalize();
        if (Files.exists(external.resolve("microgrid_business.py"))) {
            return external;
        }

        return extractBundledAlgorithm();
    }

    private Path extractBundledAlgorithm() throws IOException {
        Path target = projectRoot().resolve(".mgserver").resolve("algorithm").resolve("MOIABC").normalize();
        for (String resource : BUNDLED_ALGORITHM_RESOURCES) {
            Path output = target.resolve(resource).normalize();
            if (!output.startsWith(target)) {
                throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid bundled algorithm resource: " + resource);
            }
            Files.createDirectories(output.getParent());
            try (InputStream input = DispatchTaskService.class.getClassLoader().getResourceAsStream("algorithm/MOIABC/" + resource)) {
                if (input == null) {
                    throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Bundled algorithm resource missing: " + resource);
                }
                Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    private Path runsDir() {
        return projectRoot().resolve("algorithm-runs").normalize();
    }

    private Path projectRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("MOIABC").resolve("microgrid_business.py"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("MOIABC").resolve("microgrid_business.py"))) {
            return parent;
        }
        return cwd;
    }

    private TaskDetailResponse toDetail(DispatchTask task) {
        String originalFilename = resolveOriginalFilename(task.getId());
        return new TaskDetailResponse(
                task.getId(),
                task.getName(),
                originalFilename,
                task.getStatus(),
                task.getProgress(),
                TaskDtos.estimateRemainingSeconds(task),
                task.getMessage(),
                task.getCreatedAt(),
                buildInputJson(task),
                readTaskOutputJson(task, "summary.json", "null"),
                readTaskOutputJson(task, "dispatch_curves.json", "null"),
                readTaskOutputJson(task, "solution_options.json", "null"),
                readTaskOutputJson(task, "pareto_front.json", "[]")
        );
    }

    private TaskListResponse toListResponse(DispatchTask task) {
        String originalFilename = resolveOriginalFilename(task.getId());
        ResultRecord result = resultRecordRepository.findFirstByTask_IdOrderByCreatedAtDesc(task.getId()).orElse(null);
        return TaskListResponse.from(task, originalFilename, result);
    }

    private String resolveOriginalFilename(Long taskId) {
        return forecastFileRepository.findFirstByTask_IdOrderByUploadTimeDesc(taskId)
                .map(ForecastFile::getFileName)
                .orElse(null);
    }

    private JsonNode buildInputJson(DispatchTask task) {
        ObjectNode input = objectMapper.createObjectNode();
        ForecastFile forecastFile = forecastFileRepository.findFirstByTask_IdOrderByUploadTimeDesc(task.getId()).orElse(null);
        DeviceParam param = deviceParamRepository.findFirstByTask_IdOrderByCreatedAtDesc(task.getId())
                .orElseGet(this::getCurrentDeviceParam);
        input.set("settings", objectMapper.valueToTree(SettingResponse.from(param)));
        if (forecastFile != null && forecastFile.getFilePath() != null && Files.exists(Path.of(forecastFile.getFilePath()))) {
            try {
                input.set("profiles", objectMapper.valueToTree(profileFileService.parse(Path.of(forecastFile.getFilePath()), forecastFile.getFileName())));
            } catch (Exception ex) {
                input.set("profiles", objectMapper.createArrayNode());
            }
        } else {
            input.set("profiles", objectMapper.createArrayNode());
        }
        return input;
    }

    private JsonNode readTaskOutputJson(DispatchTask task, String filename, String fallbackJson) {
        Long taskId = task.getId();
        if (taskId == null || task.getStatus() != TaskStatus.COMPLETED) {
            return readJson(fallbackJson);
        }
        Path root = runsDir();
        Path path = root.resolve("task-" + taskId).resolve("output").resolve(filename).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            return readJson(fallbackJson);
        }
        try {
            return objectMapper.readTree(path.toFile());
        } catch (JacksonException ex) {
            return readJson(fallbackJson);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null ? "null" : json);
        } catch (JacksonException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read result");
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replace("\r", " ").replace("\n", " ").trim();
        return compact.length() <= 460 ? compact : compact.substring(0, 460);
    }

    private DeviceParam buildDeviceParam(DispatchTask task, JsonNode settings) {
        DeviceParam param = new DeviceParam();
        param.setTask(task);
        param.setMicroTurbineMinKw(settings.path("microTurbineMinKw").asDouble(20.0));
        param.setMicroTurbineMaxKw(settings.path("microTurbineMaxKw").asDouble(160.0));
        param.setMicroTurbineRampUpKw(settings.path("microTurbineRampUpKw").asDouble(55.0));
        param.setMicroTurbineRampDownKw(settings.path("microTurbineRampDownKw").asDouble(55.0));
        param.setMicroTurbineUnitCost(settings.path("microTurbineUnitCost").asDouble(0.78));
        param.setBatteryCapacityKwh(settings.path("batteryCapacityKwh").asDouble(360.0));
        param.setBatteryChargeMaxKw(settings.path("batteryChargeMaxKw").asDouble(90.0));
        param.setBatteryDischargeMaxKw(settings.path("batteryDischargeMaxKw").asDouble(90.0));
        param.setBatterySocMin(settings.path("batterySocMin").asDouble(0.20));
        param.setBatterySocMax(settings.path("batterySocMax").asDouble(0.90));
        param.setBatterySocInitial(settings.path("batterySocInitial").asDouble(0.50));
        param.setGridBuyMaxKw(settings.path("gridBuyMaxKw").asDouble(240.0));
        param.setGridSellMaxKw(settings.path("gridSellMaxKw").asDouble(160.0));
        param.setRenewableCurtailmentCost(settings.path("renewableCurtailmentCost").asDouble(0.10));
        param.touch();
        return param;
    }

    private ForecastFile buildForecastFile(DispatchTask task, MultipartFile file) {
        ForecastFile forecastFile = new ForecastFile();
        forecastFile.setTask(task);
        forecastFile.setFileName(file.getOriginalFilename() == null ? "input.csv" : file.getOriginalFilename());
        forecastFile.setFilePath(runsDir().resolve("task-" + task.getId()).resolve(getStoredName(file)).toString());
        forecastFile.setFileType(fileType(file.getOriginalFilename()));
        return forecastFile;
    }

    private String getStoredName(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "input.csv" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String extension = dot >= 0 ? original.substring(dot).toLowerCase(Locale.ROOT) : ".csv";
        return "input" + extension;
    }

    private String fileType(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "csv";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT) : "csv";
    }

    private DeviceParam getCurrentDeviceParam() {
        return deviceParamRepository.findById(DEFAULT_ROW_ID)
                .orElseGet(() -> deviceParamRepository.save(new DeviceParam()));
    }
}
