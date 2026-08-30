package dev.oumuv.budlog.baby;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.OffsetDateTime;

@Getter
@Setter
public class BabyRequest {

    @NotBlank(message = "宝宝昵称不能为空")
    @Size(max = 50, message = "宝宝昵称不能超过 50 个字符")
    private String name;

    @NotNull(message = "出生时间不能为空")
    private OffsetDateTime birthTime;

    @NotBlank(message = "时区不能为空")
    @Size(max = 64, message = "时区不能超过 64 个字符")
    private String timezone;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String note;
}

