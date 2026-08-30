package dev.oumuv.budlog.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TodoTask, Long> {

    Optional<TodoTask> findByIdAndDeletedAtIsNull(Long id);

    Optional<TodoTask> findByClientRequestId(UUID clientRequestId);

    List<TodoTask> findAllByBabyIdAndDeletedAtIsNullOrderByDueTimeAsc(Long babyId);

    List<TodoTask> findAllByBabyIdAndDeletedAtIsNullAndStatusAndRemindTimeIsNotNullAndRemindTimeLessThanEqualOrderByRemindTimeAsc(
            Long babyId, TaskStatus status, OffsetDateTime now);

    List<TodoTask> findAllByBabyIdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
            Long babyId, TaskStatus status, OffsetDateTime from, OffsetDateTime to);
}

