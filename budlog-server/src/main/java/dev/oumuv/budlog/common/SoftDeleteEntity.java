package dev.oumuv.budlog.common;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.time.OffsetDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeleteEntity extends AuditedEntity {

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}

