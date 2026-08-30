package dev.oumuv.budlog.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class DailySummaryResponse {

    private int feedingCount;
    private BigDecimal bottleAmountMl;
    private long directFeedingMinutes;
    private int milkStorageCount;
    private BigDecimal storedMilkAmountMl;
    private int peeCount;
    private int poopCount;

    public static DailySummaryResponse empty() {
        return new DailySummaryResponse(0, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, 0, 0);
    }
}
