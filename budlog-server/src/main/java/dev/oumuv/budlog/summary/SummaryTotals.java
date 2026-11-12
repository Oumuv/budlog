package dev.oumuv.budlog.summary;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A single SQL aggregate row; no business records are materialized. */
@Getter
@Setter
public class SummaryTotals {

    private int feedingCount;
    private BigDecimal bottleAmountMl = BigDecimal.ZERO;
    private long directFeedingMinutes;
    private int milkStorageCount;
    private BigDecimal storedMilkAmountMl = BigDecimal.ZERO;
    private int peeCount;
    private int poopCount;
    private BigDecimal weightKg;
    private int eventCount;
}
