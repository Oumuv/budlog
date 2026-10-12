package dev.oumuv.budlog.agent.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
public class AgentCareDailyResponse {
    private LocalDate date;
    private Diaper diaper;
    private MilkStorage milkStorage;
    private Weight weight;

    @Getter @Setter
    public static class Diaper {
        private int peeRecordCount;
        private int poopRecordCount;
        private int recordCount;
    }

    @Getter @Setter
    public static class MilkStorage {
        private BigDecimal addedMl = BigDecimal.ZERO.setScale(1);
        private int recordCount;
    }

    @Getter @Setter
    public static class Weight {
        private BigDecimal latestKg;
        private OffsetDateTime measuredAt;
        private Long ageOfMeasurementDays;
    }
}
