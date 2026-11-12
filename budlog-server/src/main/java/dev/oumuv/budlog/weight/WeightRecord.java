package dev.oumuv.budlog.weight;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.oumuv.budlog.common.SoftDeleteEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName(value = "weight_record", autoResultMap = true)
public class WeightRecord extends SoftDeleteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("baby_id")
    private Long babyId;

    @TableField("measured_at")
    private OffsetDateTime measuredAt;

    @TableField("record_date")
    private LocalDate recordDate;

    @TableField("weight_kg")
    private BigDecimal weightKg;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String note;

    @TableField("client_request_id")
    private UUID clientRequestId;
}

