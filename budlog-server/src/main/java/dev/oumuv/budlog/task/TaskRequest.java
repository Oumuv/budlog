package dev.oumuv.budlog.task;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class TaskRequest {

    @NotNull(message = "请求标识不能为空")
    private UUID clientRequestId;

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 100, message = "任务标题不能超过 100 个字符")
    private String title;

    @Size(max = 1000, message = "任务说明不能超过 1000 个字符")
    private String description;

    @NotNull(message = "到期时间不能为空")
    private OffsetDateTime dueTime;

    private OffsetDateTime remindTime;
}

