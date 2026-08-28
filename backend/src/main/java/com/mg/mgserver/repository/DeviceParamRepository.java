package com.mg.mgserver.repository;

import com.mg.mgserver.domain.DeviceParam;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceParamRepository extends JpaRepository<DeviceParam, Long> {
    Optional<DeviceParam> findFirstByTask_IdOrderByCreatedAtDesc(Long taskId);
}
