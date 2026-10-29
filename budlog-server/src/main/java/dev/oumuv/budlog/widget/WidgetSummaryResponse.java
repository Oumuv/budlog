package dev.oumuv.budlog.widget;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
public class WidgetSummaryResponse {

    private OffsetDateTime generatedAt;
    private boolean configured;
    private String babyName;
    private OffsetDateTime lastFeedingTime;
    private OffsetDateTime nextExpectedFeedingTime;
    private OffsetDateTime lastPeeTime;
    private OffsetDateTime lastPoopTime;
    private Today today;

    @Getter
    @Builder
    public static class Today {

        private int feedingCount;
        private BigDecimal bottleAmountMl;
        private int peeCount;
        private int poopCount;
        private int milkStorageCount;
        private BigDecimal storedMilkAmountMl;
    }
}
