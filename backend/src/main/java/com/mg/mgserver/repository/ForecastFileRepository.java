package com.mg.mgserver.repository;

import com.mg.mgserver.domain.ForecastFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForecastFileRepository extends JpaRepository<ForecastFile, Long> {
    Optional<ForecastFile> findFirstByTask_IdOrderByUploadTimeDesc(Long taskId);

    void deleteByTask_Id(Long taskId);
}
