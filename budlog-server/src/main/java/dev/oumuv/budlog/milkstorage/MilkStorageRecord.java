package dev.oumuv.budlog.milkstorage;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.oumuv.budlog.common.SoftDeleteEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName(value = "milk_storage_record", autoResultMap = true)
public class MilkStorageRecord extends SoftDeleteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("baby_id")
    private Long babyId;

    @TableField("stored_at")
    private OffsetDateTime storedAt;

    @TableField("amount_ml")
    private BigDecimal amountMl;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String note;

    @TableField("client_request_id")
    private UUID clientRequestId;
}
