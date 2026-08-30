package dev.oumuv.budlog.feeding;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class FeedingResponse {

    private Long id;
    private UUID clientRequestId;
    private FeedingType feedingType;
    private BreastSide breastSide;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private BigDecimal amountMl;
    private String note;
    private Long durationMinutes;
    private Long minutesSincePrevious;
    private OffsetDateTime nextExpectedTime;
}

