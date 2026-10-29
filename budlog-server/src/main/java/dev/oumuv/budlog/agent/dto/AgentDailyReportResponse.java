package dev.oumuv.budlog.agent.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
public class AgentDailyReportResponse {
    private LocalDate date;
    private String timezone;
    private AgentProfileResponse baby;
    private AgentDailyFeedingResponse feeding;
    private AgentCareDailyResponse.Diaper diaper;
    private AgentCareDailyResponse.MilkStorage milkStorage;
    private AgentCareDailyResponse.Weight weight;
    private AgentDataQualityResponse dataQuality;
    private OffsetDateTime generatedAt;
}
