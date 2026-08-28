package com.mg.mgserver.repository;

import com.mg.mgserver.domain.DispatchTask;
import com.mg.mgserver.domain.TaskStatus;
import com.mg.mgserver.domain.UserAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchTaskRepository extends JpaRepository<DispatchTask, Long> {
    List<DispatchTask> findByUserOrderByCreatedAtDesc(UserAccount user);

    List<DispatchTask> findAllByOrderByCreatedAtDesc();

    List<DispatchTask> findByStatusOrderByCreatedAtDesc(TaskStatus status);

    List<DispatchTask> findByStatusOrderByCreatedAtAsc(TaskStatus status);

    long countByStatus(TaskStatus status);

    long countByUser(UserAccount user);
}
