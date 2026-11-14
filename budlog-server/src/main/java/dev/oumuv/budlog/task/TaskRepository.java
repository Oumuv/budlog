package dev.oumuv.budlog.task;

import dev.oumuv.budlog.common.RecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface TaskRepository extends RecordMapper<TodoTask> {
    default Optional<TodoTask> findByIdAndDeletedAtIsNullForUpdate(Long id) {
        return Optional.ofNullable(selectOne(new QueryWrapper<TodoTask>()
                .eq("id", id).isNull("deleted_at").last("FOR UPDATE")));
    }

    default List<TodoTask> findFiltered(Long babyId, TaskStatus status,
                                       OffsetDateTime from, OffsetDateTime to, Integer limit) {
        QueryWrapper<TodoTask> query = between(babyId, "due_time", from, to);
        if (status != null) query.eq("status", status.name());
        query.orderByAsc("due_time", "id");
        if (limit != null) {
            if (limit < 1 || limit > 100) throw new IllegalArgumentException("Task limit must be between 1 and 100");
            query.last("LIMIT " + limit);
        }
        return selectList(query);
    }

    default List<TodoTask> findAllByBabyIdAndDeletedAtIsNullAndStatusAndRemindTimeIsNotNullAndRemindTimeLessThanEqualOrderByRemindTimeAsc(
            Long babyId, TaskStatus status, OffsetDateTime now) {
        return selectList(live(babyId).eq("status", status.name()).isNotNull("remind_time")
                .le("remind_time", now).orderByAsc("remind_time"));
    }

    default List<TodoTask> findAllByBabyIdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
            Long babyId, TaskStatus status, OffsetDateTime from, OffsetDateTime to) {
        return selectList(between(babyId, "completed_at", from, to)
                .eq("status", status.name()).orderByDesc("completed_at"));
    }
}
