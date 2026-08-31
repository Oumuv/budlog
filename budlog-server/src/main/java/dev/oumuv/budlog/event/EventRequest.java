package dev.oumuv.budlog.event;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class EventRequest {

    @NotNull(message = "请求标识不能为空")
    private UUID clientRequestId;

    @NotNull(message = "事件分类不能为空")
    private EventType eventType;

    @NotBlank(message = "事件标题不能为空")
    @Size(max = 100, message = "事件标题不能超过 100 个字符")
    private String title;

    @NotNull(message = "发生时间不能为空")
    private OffsetDateTime occurredAt;

    @Size(max = 1000, message = "备注不能超过 1000 个字符")
    private String note;
}

