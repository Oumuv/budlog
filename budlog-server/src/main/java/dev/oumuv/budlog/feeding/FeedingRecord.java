package dev.oumuv.budlog.feeding;

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
@TableName(value = "feeding_record", autoResultMap = true)
public class FeedingRecord extends SoftDeleteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("baby_id")
    private Long babyId;

    @TableField("feeding_type")
    private FeedingType feedingType;

    @TableField(value = "breast_side", updateStrategy = FieldStrategy.IGNORED)
    private BreastSide breastSide;

    @TableField("start_time")
    private OffsetDateTime startTime;

    @TableField(value = "end_time", updateStrategy = FieldStrategy.IGNORED)
    private OffsetDateTime endTime;

    @TableField(value = "amount_ml", updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal amountMl;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String note;

    @TableField("client_request_id")
    private UUID clientRequestId;
}

