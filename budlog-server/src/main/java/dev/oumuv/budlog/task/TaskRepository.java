package dev.oumuv.budlog.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TodoTask, Long> {

    Optional<TodoTask> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from TodoTask task where task.id = :id and task.deletedAt is null")
    Optional<TodoTask> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

    Optional<TodoTask> findByClientRequestId(UUID clientRequestId);

    List<TodoTask> findAllByBabyIdAndDeletedAtIsNullOrderByDueTimeAsc(Long babyId);

    List<TodoTask> findAllByBabyIdAndDeletedAtIsNullAndStatusAndRemindTimeIsNotNullAndRemindTimeLessThanEqualOrderByRemindTimeAsc(
            Long babyId, TaskStatus status, OffsetDateTime now);

    List<TodoTask> findAllByBabyIdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
            Long babyId, TaskStatus status, OffsetDateTime from, OffsetDateTime to);
}
