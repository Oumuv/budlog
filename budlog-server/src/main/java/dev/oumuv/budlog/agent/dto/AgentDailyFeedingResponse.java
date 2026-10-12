package dev.oumuv.budlog.agent.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
public class AgentDailyFeedingResponse {
    private LocalDate date;
    private int recordCount;
    private int sessionCount;
    private int breastBottleRecordCount;
    private int formulaBottleRecordCount;
    private int directFeedingRecordCount;
    private BigDecimal breastBottleMl = BigDecimal.ZERO.setScale(1);
    private BigDecimal formulaBottleMl = BigDecimal.ZERO.setScale(1);
    private BigDecimal measuredBottleIntakeMl = BigDecimal.ZERO.setScale(1);
    private Long directFeedingMinutes; // null if at least one direct session lacks a valid end
    private boolean totalIntakeKnown = true;
    private boolean measuredBottleIntakeKnown = true; // false if a bottle record has no amount
    private BigDecimal averageBottleMl;
    private Long averageIntervalMinutes;
    private Long longestRecordedIntervalMinutes;
    private OffsetDateTime lastFeedingAt;
}
