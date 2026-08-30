package dev.oumuv.budlog.setting;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    private Short id;

    @Column(name = "default_feeding_interval_min", nullable = false)
    private Integer defaultFeedingIntervalMin;

    @Column(name = "feeding_interval_anchor", nullable = false, length = 16)
    private String feedingIntervalAnchor;

    @Column(name = "reminder_sound_enabled", nullable = false)
    private Boolean reminderSoundEnabled;

    @Column(name = "reminder_vibrate_enabled", nullable = false)
    private Boolean reminderVibrateEnabled;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PreUpdate
    public void beforeUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

