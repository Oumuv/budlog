package dev.oumuv.budlog.milestone;

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
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "custom_milestone")
public class CustomMilestone extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baby_id", nullable = false)
    private BabyProfile baby;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "target_time", nullable = false)
    private OffsetDateTime targetTime;

    @Column(length = 500)
    private String note;

    @Column(name = "client_request_id", nullable = false, unique = true)
    private UUID clientRequestId;
}

