package dev.oumuv.budlog.agent.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class AgentFeedingTrendsResponse {
    private int days;
    private LocalDate endDate;
    private LocalDate previousFrom;
    private LocalDate previousTo;
    private List<AgentDailyFeedingResponse> daily;
    private BigDecimal measuredBottleIntakeMl;
    private BigDecimal previousMeasuredBottleIntakeMl;
    private BigDecimal measuredBottleChangePercent; // unknown with empty preceding records or unknown direct intake
    private int sessionCount;
    private int previousSessionCount;
    private boolean totalIntakeKnown;
    private boolean previousTotalIntakeKnown;
    private boolean measuredBottleIntakeKnown;
    private boolean previousMeasuredBottleIntakeKnown;
    private boolean comparisonAvailable;
}
