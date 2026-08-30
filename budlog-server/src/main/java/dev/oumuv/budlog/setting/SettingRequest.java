package dev.oumuv.budlog.setting;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class SettingRequest {

    @NotNull(message = "默认喂奶间隔不能为空")
    @Min(value = 30, message = "默认喂奶间隔不能少于 30 分钟")
    @Max(value = 720, message = "默认喂奶间隔不能超过 720 分钟")
    private Integer defaultFeedingIntervalMin;

    @NotNull(message = "提醒声音设置不能为空")
    private Boolean reminderSoundEnabled;

    @NotNull(message = "振动设置不能为空")
    private Boolean reminderVibrateEnabled;
}

