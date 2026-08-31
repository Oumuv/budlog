package dev.oumuv.budlog.weight;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class WeightRequest {

    @NotNull(message = "请求标识不能为空")
    private UUID clientRequestId;

    @NotNull(message = "测量时间不能为空")
    private OffsetDateTime measuredAt;

    @NotNull(message = "体重不能为空")
    @DecimalMin(value = "0.100", message = "体重不能小于 0.100 kg")
    @DecimalMax(value = "100.000", message = "体重不能大于 100.000 kg")
    @Digits(integer = 3, fraction = 3, message = "体重最多保留 3 位小数")
    private BigDecimal weightKg;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String note;
}

