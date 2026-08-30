package dev.oumuv.budlog.feeding;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class FeedingRequest {

    @NotNull(message = "请求标识不能为空")
    private UUID clientRequestId;

    @NotNull(message = "喂奶类型不能为空")
    private FeedingType feedingType;

    private BreastSide breastSide;

    @NotNull(message = "开始时间不能为空")
    private OffsetDateTime startTime;

    private OffsetDateTime endTime;

    @DecimalMin(value = "0.1", message = "奶量必须大于 0")
    @DecimalMax(value = "1000", message = "奶量不能超过 1000 ml")
    private BigDecimal amountMl;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String note;
}

