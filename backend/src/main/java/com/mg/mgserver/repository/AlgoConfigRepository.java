package com.mg.mgserver.repository;

import com.mg.mgserver.domain.AlgoConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlgoConfigRepository extends JpaRepository<AlgoConfig, Long> {
    Optional<AlgoConfig> findFirstByTask_IdOrderByUpdatedAtDesc(Long taskId);
}
