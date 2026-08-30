package dev.oumuv.budlog.timeline;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class TimelineItemResponse {

    private Long id;
    private String category;
    private String recordType;
    private OffsetDateTime eventTime;
    private String title;
    private String subtitle;
}

