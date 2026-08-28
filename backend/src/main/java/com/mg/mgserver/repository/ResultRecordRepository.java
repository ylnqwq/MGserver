package com.mg.mgserver.repository;

import com.mg.mgserver.domain.ResultRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultRecordRepository extends JpaRepository<ResultRecord, Long> {
    Optional<ResultRecord> findFirstByTask_IdOrderByCreatedAtDesc(Long taskId);

    void deleteByTask_Id(Long taskId);
}
