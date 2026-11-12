package dev.oumuv.budlog.milestone;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.oumuv.budlog.common.SoftDeleteEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName(value = "custom_milestone", autoResultMap = true)
public class CustomMilestone extends SoftDeleteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("baby_id")
    private Long babyId;

    private String title;

    @TableField("target_time")
    private OffsetDateTime targetTime;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String note;

    @TableField("client_request_id")
    private UUID clientRequestId;
}

