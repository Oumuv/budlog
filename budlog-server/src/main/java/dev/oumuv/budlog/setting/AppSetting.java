package dev.oumuv.budlog.setting;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName(value = "app_setting", autoResultMap = true)
public class AppSetting {

    @TableId(type = IdType.INPUT)
    private Short id;

    @TableField("default_feeding_interval_min")
    private Integer defaultFeedingIntervalMin;

    @TableField("feeding_interval_anchor")
    private String feedingIntervalAnchor;

    @TableField("reminder_sound_enabled")
    private Boolean reminderSoundEnabled;

    @TableField("reminder_vibrate_enabled")
    private Boolean reminderVibrateEnabled;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}

