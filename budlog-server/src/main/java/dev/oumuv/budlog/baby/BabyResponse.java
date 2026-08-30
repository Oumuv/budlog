package dev.oumuv.budlog.baby;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class BabyResponse {

    private Long id;
    private String name;
    private OffsetDateTime birthTime;
    private String timezone;
    private String note;
    private long ageDayNumber;
    private long ageDurationDays;
    private long ageDurationHours;
    private int ageMonths;
    private int ageRemainingDays;
}

