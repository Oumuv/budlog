package dev.oumuv.budlog.task;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.TimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {

    private final TaskRepository repository;
    private final BabyService babyService;
    private final TimeService timeService;

    public TaskService(TaskRepository repository, BabyService babyService, TimeService timeService) {
        this.repository = repository;
        this.babyService = babyService;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(TaskStatus status, OffsetDateTime from, OffsetDateTime to) {
        BabyProfile baby = babyService.requireCurrent();
        List<TaskResponse> result = new ArrayList<TaskResponse>();
        for (TodoTask task : repository.findAllByBabyIdAndDeletedAtIsNullOrderByDueTimeAsc(baby.getId())) {
            if (status != null && task.getStatus() != status) {
                continue;
            }
            if (from != null && task.getDueTime().isBefore(from)) {
                continue;
            }
            if (to != null && !task.getDueTime().isBefore(to)) {
                continue;
            }
            result.add(toResponse(task));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        TodoTask duplicate = repository.findByClientRequestId(request.getClientRequestId()).orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate);
        }
        TodoTask task = new TodoTask();
        task.setBaby(babyService.requireCurrent());
        task.setClientRequestId(request.getClientRequestId());
        task.setStatus(TaskStatus.TODO);
        apply(task, request);
        return toResponse(repository.save(task));
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        TodoTask task = require(id);
        apply(task, request);
        return toResponse(repository.save(task));
    }

    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatus status) {
        TodoTask task = require(id);
        if (task.getStatus() == status) {
            return toResponse(task);
        }
        task.setStatus(status);
        task.setCompletedAt(status == TaskStatus.DONE ? timeService.now() : null);
        return toResponse(repository.save(task));
    }

    @Transactional
    public void delete(Long id) {
        TodoTask task = require(id);
        task.setDeletedAt(timeService.now());
        repository.save(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> dueReminders() {
        BabyProfile baby = babyService.requireCurrent();
        List<TaskResponse> result = new ArrayList<TaskResponse>();
        for (TodoTask task : repository
                .findAllByBabyIdAndDeletedAtIsNullAndStatusAndRemindTimeIsNotNullAndRemindTimeLessThanEqualOrderByRemindTimeAsc(
                        baby.getId(), TaskStatus.TODO, timeService.now())) {
            result.add(toResponse(task));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TodoTask> completedBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return repository
                .findAllByBabyIdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
                        babyId, TaskStatus.DONE, from, to);
    }

    public TaskResponse toResponse(TodoTask task) {
        OffsetDateTime now = timeService.now();
        return TaskResponse.builder()
                .id(task.getId())
                .clientRequestId(task.getClientRequestId())
                .title(task.getTitle())
                .description(task.getDescription())
                .dueTime(task.getDueTime())
                .remindTime(task.getRemindTime())
                .status(task.getStatus())
                .completedAt(task.getCompletedAt())
                .overdue(task.getStatus() == TaskStatus.TODO && task.getDueTime().isBefore(now))
                .reminderDue(task.getStatus() == TaskStatus.TODO
                        && task.getRemindTime() != null
                        && !task.getRemindTime().isAfter(now))
                .build();
    }

    private void apply(TodoTask task, TaskRequest request) {
        if (request.getRemindTime() != null && request.getRemindTime().isAfter(request.getDueTime())) {
            throw BusinessException.validation("提醒时间不能晚于到期时间");
        }
        task.setTitle(request.getTitle().trim());
        task.setDescription(normalize(request.getDescription()));
        task.setDueTime(request.getDueTime());
        task.setRemindTime(request.getRemindTime());
    }

    private TodoTask require(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> BusinessException.notFound("任务不存在或已删除"));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
