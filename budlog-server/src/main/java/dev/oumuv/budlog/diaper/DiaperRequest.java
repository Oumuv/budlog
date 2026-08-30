package dev.oumuv.budlog.diaper;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class DiaperRequest {

    @NotNull(message = "请求标识不能为空")
    private UUID clientRequestId;

    @NotNull(message = "尿便类型不能为空")
    private DiaperType recordType;

    @NotNull(message = "发生时间不能为空")
    private OffsetDateTime recordTime;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String note;
}

