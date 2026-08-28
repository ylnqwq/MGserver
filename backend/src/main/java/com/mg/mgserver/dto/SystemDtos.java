package com.mg.mgserver.dto;

import com.mg.mgserver.domain.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;

public final class SystemDtos {
    private SystemDtos() {
    }

    public record ServerStatusResponse(
            LocalDateTime serverTime,
            int runningTasks,
            int maxRunningTasks,
            double cpuLoadPercent,
            double memoryUsedPercent,
            long memoryUsedMb,
            long memoryMaxMb,
            List<ServerTaskResponse> runningTaskList,
            List<ServerTaskResponse> queuedTaskList
    ) {
    }

    public record ServerTaskResponse(
            Long id,
            String name,
            String username,
            TaskStatus status,
            int progress,
            long estimatedRemainingSeconds,
            LocalDateTime createdAt,
            boolean mine,
            String priority
    ) {
    }
}
