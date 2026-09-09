package dev.oumuv.budlog.calendar;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.diaper.DiaperService;
import dev.oumuv.budlog.diaper.DiaperType;
import dev.oumuv.budlog.event.EventRecord;
import dev.oumuv.budlog.event.EventService;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingService;
import dev.oumuv.budlog.feeding.FeedingType;
import dev.oumuv.budlog.milestone.MilestoneResponse;
import dev.oumuv.budlog.milestone.MilestoneService;
import dev.oumuv.budlog.milkstorage.MilkStorageRecord;
import dev.oumuv.budlog.milkstorage.MilkStorageService;
import dev.oumuv.budlog.task.TaskResponse;
import dev.oumuv.budlog.task.TaskService;
import dev.oumuv.budlog.weight.WeightRecord;
import dev.oumuv.budlog.weight.WeightService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarService {
    private final BabyService babyService;
    private final TimeService timeService;
    private final TaskService taskService;
    private final MilestoneService milestoneService;
    private final EventService eventService;
    private final FeedingService feedingService;
    private final DiaperService diaperService;
    private final MilkStorageService milkStorageService;
    private final WeightService weightService;

    /** The end date is exclusive; one request covers at most a six-week month grid. */
    @Transactional(readOnly = true)
    public CalendarResponse get(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from) || ChronoUnit.DAYS.between(from, to) > 42) {
            throw BusinessException.validation("日历日期范围需为 1 至 42 天，结束日期不包含在内");
        }
        BabyProfile baby = babyService.requireCurrent();
        ZoneId zone = timeService.zone(baby.getTimezone());
        OffsetDateTime start = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = to.atStartOfDay(zone).toOffsetDateTime();
        List<CalendarItemResponse> items = new ArrayList<CalendarItemResponse>();

        // Tasks appear once, on their scheduled due date, regardless of completion date/status.
        for (TaskResponse task : taskService.list(null, start, end)) {
            items.add(item("TASK", task.getId(), task.getStatus().name(), task.getDueTime(), task.getTitle(), zone)
                    .overdue(task.isOverdue()).build());
        }
        for (MilestoneResponse milestone : milestoneService.list()) {
            if (milestone.getTargetTime().isBefore(start) || !milestone.getTargetTime().isBefore(end)) continue;
            items.add(item("MILESTONE", milestone.getId(), milestone.getCode(), milestone.getTargetTime(),
                    milestone.getTitle(), zone).build());
        }
        for (EventRecord record : eventService.recordsBetween(baby.getId(), start, end)) {
            items.add(item("EVENT", record.getId(), record.getEventType().name(), record.getOccurredAt(),
                    record.getTitle(), zone).build());
        }
        // Range queries are unpaged so busy months cannot silently lose records after item 100.
        for (FeedingRecord record : feedingService.recordsBetween(baby.getId(), start, end)) {
            String title = record.getFeedingType() == FeedingType.BREAST_DIRECT ? "母乳亲喂"
                    : record.getFeedingType() == FeedingType.BREAST_BOTTLE ? "母乳瓶喂" : "奶粉瓶喂";
            String subtitle = record.getAmountMl() == null ? null
                    : record.getAmountMl().stripTrailingZeros().toPlainString() + " ml";
            items.add(item("FEEDING", record.getId(), record.getFeedingType().name(), record.getStartTime(),
                    title, zone).subtitle(subtitle).build());
        }
        for (DiaperRecord record : diaperService.recordsBetween(baby.getId(), start, end)) {
            String title = record.getRecordType() == DiaperType.PEE ? "尿尿"
                    : record.getRecordType() == DiaperType.POOP ? "便便" : "尿便都有";
            items.add(item("DIAPER", record.getId(), record.getRecordType().name(), record.getRecordTime(),
                    title, zone).build());
        }
        for (MilkStorageRecord record : milkStorageService.recordsBetween(baby.getId(), start, end)) {
            items.add(item("MILK_STORAGE", record.getId(), "STORED", record.getStoredAt(), "存奶", zone)
                    .subtitle(record.getAmountMl().stripTrailingZeros().toPlainString() + " ml").build());
        }
        for (WeightRecord record : weightService.recordsBetween(baby.getId(), start, end)) {
            items.add(item("WEIGHT", record.getId(), "MEASURED", record.getMeasuredAt(), "体重", zone)
                    .subtitle(record.getWeightKg().stripTrailingZeros().toPlainString() + " kg").build());
        }
        items.sort(Comparator.comparing((CalendarItemResponse item) -> item.getEventTime().toInstant())
                .thenComparing(CalendarItemResponse::getKey));
        return new CalendarResponse(from, to, zone.getId(), items);
    }

    private CalendarItemResponse.CalendarItemResponseBuilder item(
            String category, Long id, String recordType, OffsetDateTime time, String title, ZoneId zone) {
        return CalendarItemResponse.builder()
                .key(category + ":" + (id == null ? recordType : id))
                .id(id).category(category).recordType(recordType)
                .date(time.atZoneSameInstant(zone).toLocalDate())
                .eventTime(time).title(title);
    }
}
