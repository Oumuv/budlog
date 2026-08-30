package dev.oumuv.budlog.timeline;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.TimeRange;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.dashboard.DailySummaryResponse;
import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.diaper.DiaperService;
import dev.oumuv.budlog.diaper.DiaperType;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingService;
import dev.oumuv.budlog.feeding.FeedingType;
import dev.oumuv.budlog.task.TaskService;
import dev.oumuv.budlog.task.TodoTask;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TimelineService {

    private final BabyService babyService;
    private final FeedingService feedingService;
    private final DiaperService diaperService;
    private final TaskService taskService;
    private final TimeService timeService;

    public TimelineService(
            BabyService babyService,
            FeedingService feedingService,
            DiaperService diaperService,
            TaskService taskService,
            TimeService timeService) {
        this.babyService = babyService;
        this.feedingService = feedingService;
        this.diaperService = diaperService;
        this.taskService = taskService;
        this.timeService = timeService;
    }

    public TimelineResponse get(LocalDate date) {
        BabyProfile baby = babyService.requireCurrent();
        LocalDate resolvedDate = date == null ? timeService.today(baby.getTimezone()) : date;
        TimeRange range = timeService.day(resolvedDate, baby.getTimezone());
        List<FeedingRecord> feedings = feedingService.recordsBetween(baby.getId(), range.getStart(), range.getEnd());
        List<DiaperRecord> diapers = diaperService.recordsBetween(baby.getId(), range.getStart(), range.getEnd());
        List<TodoTask> tasks = taskService.completedBetween(baby.getId(), range.getStart(), range.getEnd());

        List<TimelineItemResponse> items = new ArrayList<TimelineItemResponse>();
        for (FeedingRecord record : feedings) {
            items.add(feedingItem(record));
        }
        for (DiaperRecord record : diapers) {
            items.add(diaperItem(record));
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
        items.sort(Comparator.comparing(TimelineItemResponse::getEventTime).reversed());
        return new TimelineResponse(resolvedDate, summary(feedings, diapers), items);
    }

    private DailySummaryResponse summary(List<FeedingRecord> feedings, List<DiaperRecord> diapers) {
        BigDecimal bottleAmount = BigDecimal.ZERO;
        long directMinutes = 0;
        for (FeedingRecord record : feedings) {
            if (record.getFeedingType() != FeedingType.BREAST_DIRECT && record.getAmountMl() != null) {
                bottleAmount = bottleAmount.add(record.getAmountMl());
            }
            if (record.getFeedingType() == FeedingType.BREAST_DIRECT && record.getEndTime() != null) {
                directMinutes += Math.max(0, Duration.between(record.getStartTime(), record.getEndTime()).toMinutes());
            }
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
        return new DailySummaryResponse(feedings.size(), bottleAmount, directMinutes, pee, poop);
    }

    private TimelineItemResponse feedingItem(FeedingRecord record) {
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
                .build();
    }

    private TimelineItemResponse diaperItem(DiaperRecord record) {
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
                .build();
    }
}
