package dev.oumuv.budlog.milestone;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class MilestoneRequest {

    @NotNull(message = "请求标识不能为空")
    private UUID clientRequestId;

    @NotBlank(message = "纪念日名称不能为空")
    @Size(max = 100, message = "纪念日名称不能超过 100 个字符")
    private String title;

    @NotNull(message = "目标时间不能为空")
    private OffsetDateTime targetTime;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String note;
}

