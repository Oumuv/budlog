package dev.oumuv.budlog.agent.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class AgentWeightTrendsResponse {
    private LocalDate from;
    private LocalDate to;
    private List<Point> measurements;

    @Getter @Setter
    public static class Point {
        private OffsetDateTime measuredAt;
        private BigDecimal weightKg;
        private BigDecimal deltaGrams; // null for first point
        private BigDecimal gramsPerDay; // null for first point or zero time difference
    }
}
