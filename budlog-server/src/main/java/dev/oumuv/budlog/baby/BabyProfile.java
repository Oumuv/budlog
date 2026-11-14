package dev.oumuv.budlog.baby;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.oumuv.budlog.common.AuditedEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName(value = "baby_profile", autoResultMap = true)
public class BabyProfile extends AuditedEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("birth_time")
    private OffsetDateTime birthTime;

    private String timezone;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String note;
}

