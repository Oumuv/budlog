package dev.oumuv.budlog.common;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public abstract class SoftDeleteEntity extends AuditedEntity {

    @TableField(value = "deleted_at", updateStrategy = FieldStrategy.IGNORED)
    private OffsetDateTime deletedAt;
}

