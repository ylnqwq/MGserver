package com.mg.mgserver.service;

import com.mg.mgserver.repository.DispatchTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class SystemStatusServiceTest {

    @Mock
    DispatchTaskRepository taskRepository;

    @Mock
    SettingService settingService;

    @Test
    void currentMaxRunningTasksIsFixedAtFour() {
        SystemStatusService service = new SystemStatusService(taskRepository, settingService);

        assertEquals(4, service.currentMaxRunningTasks());
    }
}
