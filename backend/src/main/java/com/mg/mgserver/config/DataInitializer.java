package com.mg.mgserver.config;

import com.mg.mgserver.domain.AlgoConfig;
import com.mg.mgserver.domain.DeviceParam;
import com.mg.mgserver.repository.AlgoConfigRepository;
import com.mg.mgserver.repository.DeviceParamRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {
    private static final long DEFAULT_ROW_ID = 0L;
    private static final String ACTIVE_STATUS = "ACTIVE";

    @Bean
    CommandLineRunner initDefaults(JdbcTemplate jdbcTemplate,
                                   DeviceParamRepository deviceParamRepository,
                                   AlgoConfigRepository algoConfigRepository) {
        return args -> {
            migrateUserStatus(jdbcTemplate);
            migrateDispatchTaskMetrics(jdbcTemplate);
            ensureDeviceParamDefault(jdbcTemplate, deviceParamRepository);
            ensureAlgoConfigDefault(jdbcTemplate, algoConfigRepository);
        };
    }

    private void migrateUserStatus(JdbcTemplate jdbcTemplate) {
        executeBestEffort(jdbcTemplate, """
                ALTER TABLE user_account
                MODIFY COLUMN status varchar(20) NOT NULL DEFAULT 'ACTIVE'
                """);
        executeBestEffort(jdbcTemplate, "UPDATE user_account SET status = ? WHERE status IS NULL OR status = ''", ACTIVE_STATUS);
    }

    private void migrateDispatchTaskMetrics(JdbcTemplate jdbcTemplate) {
        executeBestEffort(jdbcTemplate, """
                ALTER TABLE dispatch_task
                MODIFY COLUMN economic_cost double NOT NULL DEFAULT 0,
                MODIFY COLUMN environment_cost double NOT NULL DEFAULT 0,
                MODIFY COLUMN renewable_utilization_rate double NOT NULL DEFAULT 0,
                MODIFY COLUMN final_soc double NOT NULL DEFAULT 0
                """);
        executeBestEffort(jdbcTemplate, """
                UPDATE dispatch_task
                SET economic_cost = 0
                WHERE economic_cost IS NULL
                """);
        executeBestEffort(jdbcTemplate, """
                UPDATE dispatch_task
                SET environment_cost = 0
                WHERE environment_cost IS NULL
                """);
        executeBestEffort(jdbcTemplate, """
                UPDATE dispatch_task
                SET renewable_utilization_rate = 0
                WHERE renewable_utilization_rate IS NULL
                """);
        executeBestEffort(jdbcTemplate, """
                UPDATE dispatch_task
                SET final_soc = 0
                WHERE final_soc IS NULL
                """);
    }

    private void executeBestEffort(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        try {
            if (args == null || args.length == 0) {
                jdbcTemplate.execute(sql);
            } else {
                jdbcTemplate.update(sql, args);
            }
        } catch (DataAccessException ignored) {
            // Test databases may not carry legacy columns yet.
        }
    }

    private void ensureDeviceParamDefault(JdbcTemplate jdbcTemplate, DeviceParamRepository deviceParamRepository) {
        if (deviceParamRepository.existsById(DEFAULT_ROW_ID)) {
            jdbcTemplate.update("DELETE FROM device_param WHERE task_id IS NULL AND param_id <> 0");
            jdbcTemplate.update("UPDATE device_param SET task_id = NULL WHERE param_id = 0");
            return;
        }

        Long sourceId = findDefaultDeviceParamId(jdbcTemplate);
        if (sourceId == null) {
            DeviceParam defaultRow = deviceParamRepository.saveAndFlush(new DeviceParam());
            sourceId = defaultRow.getId();
        }
        if (sourceId != null && sourceId != DEFAULT_ROW_ID) {
            jdbcTemplate.update("UPDATE device_param SET param_id = 0 WHERE param_id = ?", sourceId);
        }
        jdbcTemplate.update("UPDATE device_param SET task_id = NULL WHERE param_id = 0");
        jdbcTemplate.update("DELETE FROM device_param WHERE task_id IS NULL AND param_id <> 0");
    }

    private void ensureAlgoConfigDefault(JdbcTemplate jdbcTemplate, AlgoConfigRepository algoConfigRepository) {
        if (algoConfigRepository.existsById(DEFAULT_ROW_ID)) {
            jdbcTemplate.update("DELETE FROM algo_config WHERE task_id IS NULL AND algo_id <> 0");
            jdbcTemplate.update("UPDATE algo_config SET task_id = NULL WHERE algo_id = 0");
            return;
        }

        Long sourceId = findDefaultAlgoConfigId(jdbcTemplate);
        if (sourceId == null) {
            AlgoConfig defaultRow = algoConfigRepository.saveAndFlush(new AlgoConfig());
            sourceId = defaultRow.getId();
        }
        if (sourceId != null && sourceId != DEFAULT_ROW_ID) {
            jdbcTemplate.update("UPDATE algo_config SET algo_id = 0 WHERE algo_id = ?", sourceId);
        }
        jdbcTemplate.update("UPDATE algo_config SET task_id = NULL WHERE algo_id = 0");
        jdbcTemplate.update("DELETE FROM algo_config WHERE task_id IS NULL AND algo_id <> 0");
    }

    private Long findDefaultDeviceParamId(JdbcTemplate jdbcTemplate) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT param_id FROM device_param WHERE task_id IS NULL ORDER BY create_time DESC LIMIT 1",
                    Long.class
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private Long findDefaultAlgoConfigId(JdbcTemplate jdbcTemplate) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT algo_id FROM algo_config WHERE task_id IS NULL ORDER BY update_time DESC LIMIT 1",
                    Long.class
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }
}
