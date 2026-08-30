package dev.oumuv.budlog.task;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class TaskStatusRequest {

    @NotNull(message = "任务状态不能为空")
    private TaskStatus status;
}

