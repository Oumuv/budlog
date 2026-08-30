package dev.oumuv.budlog.task;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class TaskResponse {

    private Long id;
    private UUID clientRequestId;
    private String title;
    private String description;
    private OffsetDateTime dueTime;
    private OffsetDateTime remindTime;
    private TaskStatus status;
    private OffsetDateTime completedAt;
    private boolean overdue;
    private boolean reminderDue;
}

