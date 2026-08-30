package dev.oumuv.budlog.milkstorage;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.common.SoftDeleteEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
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
@Table(name = "milk_storage_record")
public class MilkStorageRecord extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baby_id", nullable = false)
    private BabyProfile baby;

    @Column(name = "stored_at", nullable = false)
    private OffsetDateTime storedAt;

    @Column(name = "amount_ml", nullable = false, precision = 6, scale = 1)
    private BigDecimal amountMl;

    @Column(length = 500)
    private String note;

    @Column(name = "client_request_id", nullable = false, unique = true)
    private UUID clientRequestId;
}
