package dev.oumuv.budlog.widget;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.diaper.DiaperService;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingService;
import dev.oumuv.budlog.setting.SettingService;
import dev.oumuv.budlog.summary.SummaryService;
import dev.oumuv.budlog.summary.SummaryTotals;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class WidgetService {

    private final BabyService babyService;
    private final FeedingService feedingService;
    private final DiaperService diaperService;
    private final SettingService settingService;
    private final SummaryService summaryService;
    private final TimeService timeService;

    public WidgetService(BabyService babyService, FeedingService feedingService, DiaperService diaperService,
                         SettingService settingService, SummaryService summaryService, TimeService timeService) {
        this.babyService = babyService;
        this.feedingService = feedingService;
        this.diaperService = diaperService;
        this.settingService = settingService;
        this.summaryService = summaryService;
        this.timeService = timeService;
    }

    public WidgetSummaryResponse getSummary() {
        BabyProfile baby = babyService.findCurrent().orElse(null);
        SummaryTotals summary = new SummaryTotals();
        FeedingRecord feeding = null;
        DiaperRecord pee = null;
        DiaperRecord poop = null;
        OffsetDateTime nextFeeding = null;
        if (baby != null) {
            summary = summaryService.widget(baby, null);
            feeding = feedingService.latest(baby.getId());
            pee = diaperService.lastPee(baby.getId());
            poop = diaperService.lastPoop(baby.getId());
            if (feeding != null) {
                nextFeeding = feeding.getStartTime().plusMinutes(settingService.get().getDefaultFeedingIntervalMin());
            }
        }

        return WidgetSummaryResponse.builder()
                .generatedAt(timeService.now())
                .configured(baby != null)
                .babyName(baby == null ? null : baby.getName())
                .lastFeedingTime(feeding == null ? null : feeding.getStartTime())
                .nextExpectedFeedingTime(nextFeeding)
                .lastPeeTime(pee == null ? null : pee.getRecordTime())
                .lastPoopTime(poop == null ? null : poop.getRecordTime())
                .today(WidgetSummaryResponse.Today.builder()
                        .feedingCount(summary.getFeedingCount())
                        .bottleAmountMl(summary.getBottleAmountMl())
                        .peeCount(summary.getPeeCount())
                        .poopCount(summary.getPoopCount())
                        .milkStorageCount(summary.getMilkStorageCount())
                        .storedMilkAmountMl(summary.getStoredMilkAmountMl())
                        .build())
                .build();
    }
}
