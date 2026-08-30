package dev.oumuv.budlog.baby;

import dev.oumuv.budlog.common.AuditedEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "baby_profile")
public class BabyProfile extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "birth_time", nullable = false)
    private OffsetDateTime birthTime;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(length = 500)
    private String note;
}

