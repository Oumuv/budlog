package dev.oumuv.budlog.agent.dto;

import dev.oumuv.budlog.feeding.BreastSide;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

@Getter
public class AgentFeedingRecordResponse {
    private final Long id;
    private final FeedingType feedingType;
    private final BreastSide breastSide;
    private final OffsetDateTime startTime;
    private final OffsetDateTime endTime;
    private final BigDecimal amountMl;
    private final String note;
    private final Long durationMinutes;

    public AgentFeedingRecordResponse(FeedingRecord record) {
        this.id = record.getId();
        this.feedingType = record.getFeedingType();
        this.breastSide = record.getBreastSide();
        this.startTime = record.getStartTime();
        this.endTime = record.getEndTime();
        this.amountMl = record.getAmountMl();
        this.note = record.getNote();
        this.durationMinutes = record.getEndTime() == null || record.getEndTime().isBefore(record.getStartTime())
                ? null : Duration.between(record.getStartTime(), record.getEndTime()).toMinutes();
    }
}
