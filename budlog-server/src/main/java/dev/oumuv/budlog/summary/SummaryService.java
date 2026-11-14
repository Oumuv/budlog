package dev.oumuv.budlog.summary;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.TimeRange;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.dashboard.DailySummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class SummaryService {

    private final SummaryMapper mapper;
    private final BabyService babyService;
    private final TimeService timeService;

    public SummaryService(SummaryMapper mapper, BabyService babyService, TimeService timeService) {
        this.mapper = mapper;
        this.babyService = babyService;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DailySummaryResponse daily(LocalDate date) {
        return daily(babyService.requireCurrent(), date);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DailySummaryResponse daily(BabyProfile baby, LocalDate date) {
        TimeRange range = range(baby, date);
        SummaryTotals core = mapper.selectCore(baby.getId(), range.getStart(), range.getEnd());
        SummaryTotals extras = mapper.selectExtras(baby.getId(), range.getStart(), range.getEnd());
        return new DailySummaryResponse(
                core.getFeedingCount(), core.getBottleAmountMl(), core.getDirectFeedingMinutes(),
                core.getMilkStorageCount(), core.getStoredMilkAmountMl(), core.getPeeCount(), core.getPoopCount(),
                extras.getWeightKg(), extras.getEventCount());
    }

    /** Widget needs only feeding, diaper and milk totals; never queries weight or events. */
    @Transactional(readOnly = true)
    public SummaryTotals widget(BabyProfile baby, LocalDate date) {
        TimeRange range = range(baby, date);
        return mapper.selectCore(baby.getId(), range.getStart(), range.getEnd());
    }

    private TimeRange range(BabyProfile baby, LocalDate date) {
        LocalDate resolved = date == null ? timeService.today(baby.getTimezone()) : date;
        return timeService.day(resolved, baby.getTimezone());
    }
}
