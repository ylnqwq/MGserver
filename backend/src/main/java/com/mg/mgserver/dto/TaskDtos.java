package com.mg.mgserver.dto;

import tools.jackson.databind.JsonNode;
import com.mg.mgserver.domain.ResultRecord;
import com.mg.mgserver.domain.DispatchTask;
import com.mg.mgserver.domain.TaskStatus;
import java.time.Duration;
import java.time.LocalDateTime;

public final class TaskDtos {
    private TaskDtos() {
    }

    public record TaskListResponse(
            Long id,
            String name,
            String originalFilename,
            TaskStatus status,
            int progress,
            long estimatedRemainingSeconds,
            double economicCost,
            double environmentCost,
            double renewableUtilizationRate,
            double finalSoc,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
        public static TaskListResponse from(DispatchTask task, String originalFilename, ResultRecord result) {
            return new TaskListResponse(
                    task.getId(),
                    task.getName(),
                    originalFilename,
                    task.getStatus(),
                    task.getProgress(),
                    estimateRemainingSeconds(task),
                    result == null ? 0.0 : result.getEconomicCost(),
                    result == null ? 0.0 : result.getEnvironmentCost(),
                    result == null ? 0.0 : result.getRenewableUtilizationRate(),
                    result == null ? 0.0 : result.getFinalSoc(),
                    task.getCreatedAt(),
                    task.getCompletedAt()
            );
        }
    }

    public record TaskDetailResponse(
            Long id,
            String name,
            String originalFilename,
            TaskStatus status,
            int progress,
            long estimatedRemainingSeconds,
            String message,
            LocalDateTime createdAt,
            JsonNode input,
            JsonNode summary,
            JsonNode curves,
            JsonNode solutionOptions,
            JsonNode paretoFront
    ) {
    }

    public record RenameTaskRequest(String name) {
    }

    public static long estimateRemainingSeconds(DispatchTask task) {
        if (task.getStatus() != TaskStatus.RUNNING || task.getProgress() <= 0 || task.getProgress() >= 100) {
            return 0L;
        }
        LocalDateTime start = task.getStartTime() == null ? task.getCreatedAt() : task.getStartTime();
        long elapsedSeconds = Math.max(1L, Duration.between(start, LocalDateTime.now()).toSeconds());
        return Math.max(0L, Math.round(elapsedSeconds * (100.0 - task.getProgress()) / task.getProgress()));
    }
}
