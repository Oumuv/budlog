package dev.oumuv.budlog.task;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.oumuv.budlog.common.SoftDeleteEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName(value = "todo_task", autoResultMap = true)
public class TodoTask extends SoftDeleteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("baby_id")
    private Long babyId;

    private String title;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String description;

    @TableField("due_time")
    private OffsetDateTime dueTime;

    @TableField(value = "remind_time", updateStrategy = FieldStrategy.IGNORED)
    private OffsetDateTime remindTime;

    @TableField("recurrence_type")
    private TaskRecurrenceType recurrenceType;

    @TableField(value = "previous_task_id", updateStrategy = FieldStrategy.IGNORED)
    private Long previousTaskId;

    @TableField("next_occurrence_created")
    private boolean nextOccurrenceCreated;

    private TaskStatus status;

    @TableField(value = "completed_at", updateStrategy = FieldStrategy.IGNORED)
    private OffsetDateTime completedAt;

    @TableField("client_request_id")
    private UUID clientRequestId;
}
