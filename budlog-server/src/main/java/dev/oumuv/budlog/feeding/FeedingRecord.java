package dev.oumuv.budlog.feeding;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.common.SoftDeleteEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "feeding_record")
public class FeedingRecord extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baby_id", nullable = false)
    private BabyProfile baby;

    @Enumerated(EnumType.STRING)
    @Column(name = "feeding_type", nullable = false, length = 32)
    private FeedingType feedingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "breast_side", length = 16)
    private BreastSide breastSide;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "amount_ml", precision = 6, scale = 1)
    private BigDecimal amountMl;

    @Column(length = 500)
    private String note;

    @Column(name = "client_request_id", nullable = false, unique = true)
    private UUID clientRequestId;
}

