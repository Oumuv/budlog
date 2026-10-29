package dev.oumuv.budlog.widget;

import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.dashboard.DailySummaryResponse;
import dev.oumuv.budlog.dashboard.DashboardResponse;
import dev.oumuv.budlog.dashboard.DashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class WidgetService {

    private final DashboardService dashboardService;
    private final TimeService timeService;

    public WidgetService(DashboardService dashboardService, TimeService timeService) {
        this.dashboardService = dashboardService;
        this.timeService = timeService;
    }

    public WidgetSummaryResponse getSummary() {
        DashboardResponse dashboard = dashboardService.get(null);
        DailySummaryResponse summary = dashboard.getTodaySummary();

        return WidgetSummaryResponse.builder()
                .generatedAt(timeService.now())
                .configured(dashboard.isConfigured())
                .babyName(dashboard.getBaby() == null ? null : dashboard.getBaby().getName())
                .lastFeedingTime(dashboard.getLastFeeding() == null
                        ? null
                        : dashboard.getLastFeeding().getStartTime())
                .nextExpectedFeedingTime(dashboard.getNextExpectedFeedingTime())
                .lastPeeTime(dashboard.getLastPee() == null ? null : dashboard.getLastPee().getRecordTime())
                .lastPoopTime(dashboard.getLastPoop() == null ? null : dashboard.getLastPoop().getRecordTime())
                .today(WidgetSummaryResponse.Today.builder()
                        .feedingCount(summary == null ? 0 : summary.getFeedingCount())
                        .bottleAmountMl(summary == null ? BigDecimal.ZERO : summary.getBottleAmountMl())
                        .peeCount(summary == null ? 0 : summary.getPeeCount())
                        .poopCount(summary == null ? 0 : summary.getPoopCount())
                        .milkStorageCount(summary == null ? 0 : summary.getMilkStorageCount())
                        .storedMilkAmountMl(summary == null ? BigDecimal.ZERO : summary.getStoredMilkAmountMl())
                        .build())
                .build();
    }
}
