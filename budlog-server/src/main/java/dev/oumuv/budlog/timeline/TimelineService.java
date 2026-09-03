package dev.oumuv.budlog.timeline;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.TimeRange;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.dashboard.DailySummaryResponse;
import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.diaper.DiaperService;
import dev.oumuv.budlog.diaper.DiaperType;
import dev.oumuv.budlog.event.EventRecord;
import dev.oumuv.budlog.event.EventService;
import dev.oumuv.budlog.event.EventType;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingService;
import dev.oumuv.budlog.feeding.FeedingType;
import dev.oumuv.budlog.milkstorage.MilkStorageRecord;
import dev.oumuv.budlog.milkstorage.MilkStorageService;
import dev.oumuv.budlog.task.TaskService;
import dev.oumuv.budlog.task.TodoTask;
import dev.oumuv.budlog.weight.WeightRecord;
import dev.oumuv.budlog.weight.WeightService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TimelineService {

    private final BabyService babyService;
    private final FeedingService feedingService;
    private final DiaperService diaperService;
    private final MilkStorageService milkStorageService;
    private final TaskService taskService;
    private final WeightService weightService;
    private final EventService eventService;
    private final TimeService timeService;

    public TimelineService(
            BabyService babyService,
            FeedingService feedingService,
            DiaperService diaperService,
            MilkStorageService milkStorageService,
            TaskService taskService,
            WeightService weightService,
            EventService eventService,
            TimeService timeService) {
        this.babyService = babyService;
        this.feedingService = feedingService;
        this.diaperService = diaperService;
        this.milkStorageService = milkStorageService;
        this.taskService = taskService;
        this.weightService = weightService;
        this.eventService = eventService;
        this.timeService = timeService;
    }

    public TimelineResponse get(LocalDate date) {
        BabyProfile baby = babyService.requireCurrent();
        LocalDate resolvedDate = date == null ? timeService.today(baby.getTimezone()) : date;
        TimeRange range = timeService.day(resolvedDate, baby.getTimezone());
        List<FeedingRecord> feedings = feedingService.recordsBetween(baby.getId(), range.getStart(), range.getEnd());
        List<DiaperRecord> diapers = diaperService.recordsBetween(baby.getId(), range.getStart(), range.getEnd());
        List<MilkStorageRecord> milkStorages = milkStorageService.recordsBetween(
                baby.getId(), range.getStart(), range.getEnd());
        List<TodoTask> tasks = taskService.completedBetween(baby.getId(), range.getStart(), range.getEnd());
        List<WeightRecord> weights = weightService.recordsBetween(baby.getId(), range.getStart(), range.getEnd());
        List<EventRecord> events = eventService.recordsBetween(baby.getId(), range.getStart(), range.getEnd());

        List<TimelineItemResponse> items = new ArrayList<TimelineItemResponse>();
        List<FeedingRecord> chronologicalFeedings = new ArrayList<FeedingRecord>(feedings);
        chronologicalFeedings.sort(Comparator.comparing(FeedingRecord::getStartTime));
        FeedingRecord previousFeeding = chronologicalFeedings.isEmpty()
                ? null
                : feedingService.latestBefore(baby.getId(), range.getStart());
        OffsetDateTime previousFeedingTime = previousFeeding == null ? null : previousFeeding.getStartTime();
        OffsetDateTime feedingGroupTime = null;
        Long feedingInterval = null;
        for (FeedingRecord record : chronologicalFeedings) {
            if (!record.getStartTime().equals(feedingGroupTime)) {
                feedingInterval = minutesBetween(previousFeedingTime, record.getStartTime());
                previousFeedingTime = record.getStartTime();
                feedingGroupTime = record.getStartTime();
            }
            items.add(feedingItem(record, feedingInterval));
        }

        List<DiaperRecord> chronologicalDiapers = new ArrayList<DiaperRecord>(diapers);
        chronologicalDiapers.sort(Comparator.comparing(DiaperRecord::getRecordTime));
        DiaperRecord previousDiaper = chronologicalDiapers.isEmpty()
                ? null
                : diaperService.latestBefore(baby.getId(), range.getStart());
        OffsetDateTime previousDiaperTime = previousDiaper == null ? null : previousDiaper.getRecordTime();
        OffsetDateTime diaperGroupTime = null;
        Long diaperInterval = null;
        for (DiaperRecord record : chronologicalDiapers) {
            if (!record.getRecordTime().equals(diaperGroupTime)) {
                diaperInterval = minutesBetween(previousDiaperTime, record.getRecordTime());
                previousDiaperTime = record.getRecordTime();
                diaperGroupTime = record.getRecordTime();
            }
            items.add(diaperItem(record, diaperInterval));
        }
        for (MilkStorageRecord record : milkStorages) {
            items.add(milkStorageItem(record));
        }
        for (TodoTask task : tasks) {
            items.add(TimelineItemResponse.builder()
                    .id(task.getId())
                    .category("TASK")
                    .recordType("DONE")
                    .eventTime(task.getCompletedAt())
                    .title("完成任务")
                    .subtitle(task.getTitle())
                    .build());
        }
        for (WeightRecord record : weights) {
            items.add(weightItem(record));
        }
        for (EventRecord record : events) {
            items.add(eventItem(record));
        }
        items.sort(Comparator.comparing(TimelineItemResponse::getEventTime).reversed());
        return new TimelineResponse(resolvedDate, summary(feedings, diapers, milkStorages, weights, events), items);
    }

    private DailySummaryResponse summary(
            List<FeedingRecord> feedings,
            List<DiaperRecord> diapers,
            List<MilkStorageRecord> milkStorages,
            List<WeightRecord> weights,
            List<EventRecord> events) {
        BigDecimal bottleAmount = BigDecimal.ZERO;
        BigDecimal storedMilkAmount = BigDecimal.ZERO;
        long directMinutes = 0;
        for (FeedingRecord record : feedings) {
            if (record.getFeedingType() != FeedingType.BREAST_DIRECT && record.getAmountMl() != null) {
                bottleAmount = bottleAmount.add(record.getAmountMl());
            }
            if (record.getFeedingType() == FeedingType.BREAST_DIRECT && record.getEndTime() != null) {
                directMinutes += Math.max(0, Duration.between(record.getStartTime(), record.getEndTime()).toMinutes());
            }
        }
        for (MilkStorageRecord record : milkStorages) {
            storedMilkAmount = storedMilkAmount.add(record.getAmountMl());
        }
        int pee = 0;
        int poop = 0;
        for (DiaperRecord record : diapers) {
            if (record.getRecordType() == DiaperType.PEE || record.getRecordType() == DiaperType.BOTH) {
                pee++;
            }
            if (record.getRecordType() == DiaperType.POOP || record.getRecordType() == DiaperType.BOTH) {
                poop++;
            }
        }
        return new DailySummaryResponse(
                feedings.size(),
                bottleAmount,
                directMinutes,
                milkStorages.size(),
                storedMilkAmount,
                pee,
                poop,
                weights.isEmpty() ? null : weights.get(0).getWeightKg(),
                events.size());
    }

    private TimelineItemResponse feedingItem(FeedingRecord record, Long minutesSincePrevious) {
        String title;
        String subtitle;
        if (record.getFeedingType() == FeedingType.BREAST_DIRECT) {
            title = "母乳亲喂";
            long minutes = record.getEndTime() == null
                    ? 0
                    : Math.max(0, Duration.between(record.getStartTime(), record.getEndTime()).toMinutes());
            subtitle = minutes > 0 ? minutes + " 分钟" : "计时记录";
        } else {
            title = record.getFeedingType() == FeedingType.BREAST_BOTTLE ? "母乳瓶喂" : "奶粉瓶喂";
            subtitle = record.getAmountMl() == null
                    ? "未填写奶量"
                    : record.getAmountMl().stripTrailingZeros().toPlainString() + " ml";
        }
        return TimelineItemResponse.builder()
                .id(record.getId())
                .category("FEEDING")
                .recordType(record.getFeedingType().name())
                .eventTime(record.getStartTime())
                .title(title)
                .subtitle(subtitle)
                .minutesSincePrevious(minutesSincePrevious)
                .build();
    }

    private TimelineItemResponse diaperItem(DiaperRecord record, Long minutesSincePrevious) {
        String title = record.getRecordType() == DiaperType.PEE
                ? "尿尿"
                : record.getRecordType() == DiaperType.POOP ? "便便" : "尿便都有";
        return TimelineItemResponse.builder()
                .id(record.getId())
                .category("DIAPER")
                .recordType(record.getRecordType().name())
                .eventTime(record.getRecordTime())
                .title(title)
                .subtitle(record.getNote())
                .minutesSincePrevious(minutesSincePrevious)
                .build();
    }

    private Long minutesBetween(OffsetDateTime previous, OffsetDateTime current) {
        return previous == null ? null : Math.max(0, Duration.between(previous, current).toMinutes());
    }

    private TimelineItemResponse milkStorageItem(MilkStorageRecord record) {
        return TimelineItemResponse.builder()
                .id(record.getId())
                .category("MILK_STORAGE")
                .recordType("STORED")
                .eventTime(record.getStoredAt())
                .title("存奶")
                .subtitle(record.getAmountMl().stripTrailingZeros().toPlainString() + " ml")
                .build();
    }

    private TimelineItemResponse weightItem(WeightRecord record) {
        return TimelineItemResponse.builder()
                .id(record.getId())
                .category("WEIGHT")
                .recordType("MEASURED")
                .eventTime(record.getMeasuredAt())
                .title("体重 " + record.getWeightKg().stripTrailingZeros().toPlainString() + " kg")
                .subtitle(record.getNote())
                .build();
    }

    private TimelineItemResponse eventItem(EventRecord record) {
        String typeLabel = eventTypeLabel(record.getEventType());
        String subtitle = record.getNote() == null ? typeLabel : typeLabel + " · " + record.getNote();
        return TimelineItemResponse.builder()
                .id(record.getId())
                .category("EVENT")
                .recordType(record.getEventType().name())
                .eventTime(record.getOccurredAt())
                .title(record.getTitle())
                .subtitle(subtitle)
                .build();
    }

    private String eventTypeLabel(EventType type) {
        if (type == EventType.VACCINE) {
            return "疫苗";
        }
        if (type == EventType.DOCUMENT) {
            return "证件";
        }
        if (type == EventType.MOMENT) {
            return "小事";
        }
        return "其他";
    }
}
