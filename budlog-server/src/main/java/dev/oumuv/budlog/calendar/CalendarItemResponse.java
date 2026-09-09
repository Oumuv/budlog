package dev.oumuv.budlog.calendar;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
public class CalendarItemResponse {
    private String key;
    private Long id;
    private String category;
    private String recordType;
    private LocalDate date;
    private OffsetDateTime eventTime;
    private String title;
    private String subtitle;
    private boolean overdue;
}
