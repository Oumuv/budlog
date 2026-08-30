package dev.oumuv.budlog.milestone;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class MilestoneResponse {

    private Long id;
    private String code;
    private boolean system;
    private String title;
    private OffsetDateTime targetTime;
    private long daysDifference;
    private String note;
}

