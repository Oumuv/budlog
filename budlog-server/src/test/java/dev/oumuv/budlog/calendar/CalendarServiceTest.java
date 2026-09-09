package dev.oumuv.budlog.calendar;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.config.AppProperties;
import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.diaper.DiaperService;
import dev.oumuv.budlog.diaper.DiaperType;
import dev.oumuv.budlog.event.EventRecord;
import dev.oumuv.budlog.event.EventService;
import dev.oumuv.budlog.event.EventType;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingService;
import dev.oumuv.budlog.feeding.FeedingType;
import dev.oumuv.budlog.milestone.MilestoneResponse;
import dev.oumuv.budlog.milestone.MilestoneService;
import dev.oumuv.budlog.milkstorage.MilkStorageRecord;
import dev.oumuv.budlog.milkstorage.MilkStorageService;
import dev.oumuv.budlog.task.TaskResponse;
import dev.oumuv.budlog.task.TaskService;
import dev.oumuv.budlog.task.TaskStatus;
import dev.oumuv.budlog.weight.WeightRecord;
import dev.oumuv.budlog.weight.WeightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CalendarServiceTest {
    private final BabyService babies = mock(BabyService.class);
    private final TaskService tasks = mock(TaskService.class);
    private final MilestoneService milestones = mock(MilestoneService.class);
    private final EventService events = mock(EventService.class);
    private final FeedingService feedings = mock(FeedingService.class);
    private final DiaperService diapers = mock(DiaperService.class);
    private final MilkStorageService storage = mock(MilkStorageService.class);
    private final WeightService weights = mock(WeightService.class);
    private final BabyProfile baby = new BabyProfile();
    private final CalendarService service = new CalendarService(babies,
            new TimeService(Clock.systemUTC(), new AppProperties()), tasks, milestones,
            events, feedings, diapers, storage, weights);
    private final LocalDate from = LocalDate.parse("2026-09-01");
    private final LocalDate to = LocalDate.parse("2026-10-01");

    @BeforeEach
    void setUp() {
        baby.setId(1L);
        baby.setTimezone("Asia/Shanghai");
        when(babies.requireCurrent()).thenReturn(baby);
    }

    @Test
    void rejectsMissingReversedEmptyAndOversizedRangesBeforeReadingData() {
        assertThatThrownBy(() -> service.get(null, to)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.get(from, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.get(to, from)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.get(from, from)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.get(from, from.plusDays(43))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(tasks, events, feedings, milestones);
    }

    @Test
    void usesBabyTimezoneAndExclusiveEndAcrossDaylightSavingChange() {
        baby.setTimezone("America/New_York");
        CalendarResponse result = service.get(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-04-12"));
        OffsetDateTime start = OffsetDateTime.parse("2026-03-01T00:00:00-05:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-12T00:00:00-04:00");
        verify(tasks).list(null, start, end);
        verify(events).recordsBetween(1L, start, end);
        verify(feedings).recordsBetween(1L, start, end);
        verify(diapers).recordsBetween(1L, start, end);
        verify(storage).recordsBetween(1L, start, end);
        verify(weights).recordsBetween(1L, start, end);
        assertThat(result.getTimezone()).isEqualTo("America/New_York");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void includesEveryTaskStatusOnDueDateRatherThanCompletionDate() {
        List<TaskResponse> records = new ArrayList<TaskResponse>();
        for (TaskStatus status : TaskStatus.values()) {
            records.add(TaskResponse.builder().id((long) status.ordinal() + 1).title(status.name()).status(status)
                    .dueTime(OffsetDateTime.parse("2026-09-08T16:30:00Z"))
                    .completedAt(OffsetDateTime.parse("2026-09-10T10:00:00Z"))
                    .overdue(status == TaskStatus.TODO).build());
        }
        when(tasks.list(isNull(), any(), any())).thenReturn(records);
        List<CalendarItemResponse> result = service.get(from, to).getItems();
        assertThat(result).hasSize(3).allMatch(item -> item.getDate().equals(LocalDate.parse("2026-09-09")));
        assertThat(result).extracting(CalendarItemResponse::getRecordType).containsExactlyInAnyOrder("TODO", "DONE", "CANCELED");
        assertThat(result).filteredOn(CalendarItemResponse::isOverdue).hasSize(1);
    }

    @Test
    void filtersMilestonesAtRangeBoundariesAndKeepsSystemAndCustomIdentities() {
        when(milestones.list()).thenReturn(Arrays.asList(
                milestone(null, "FULL_MONTH", "2026-09-01T00:00:00+08:00"),
                milestone(null, "DAY_100", "2026-09-30T23:59:00+08:00"),
                milestone(1L, "CUSTOM", "2026-09-09T08:00:00+08:00"),
                milestone(2L, "CUSTOM", "2026-08-31T23:59:59+08:00"),
                milestone(null, "ONE_YEAR", "2026-10-01T00:00:00+08:00")));
        assertThat(service.get(from, to).getItems()).extracting(CalendarItemResponse::getKey)
                .containsExactly("MILESTONE:FULL_MONTH", "MILESTONE:1", "MILESTONE:DAY_100");
    }

    @Test
    void retainsMoreThanOneHundredRecordsAndGroupsUtcTimesOnLocalDay() {
        List<FeedingRecord> records = new ArrayList<FeedingRecord>();
        for (long id = 1; id <= 125; id++) {
            FeedingRecord record = new FeedingRecord();
            record.setId(id);
            record.setFeedingType(FeedingType.BREAST_BOTTLE);
            record.setAmountMl(new BigDecimal("80.0"));
            record.setStartTime(OffsetDateTime.parse("2026-09-08T23:00:00Z"));
            records.add(record);
        }
        when(feedings.recordsBetween(eq(1L), any(), any())).thenReturn(records);
        assertThat(service.get(from, to).getItems()).hasSize(125)
                .allMatch(item -> item.getDate().toString().equals("2026-09-09") && item.getSubtitle().equals("80 ml"));
    }

    @Test
    void combinesEventAndCareSourcesAndOrdersByInstant() {
        EventRecord event = new EventRecord();
        event.setId(1L); event.setTitle("接种记录"); event.setEventType(EventType.VACCINE);
        event.setOccurredAt(OffsetDateTime.parse("2026-09-09T08:00:00+08:00"));
        DiaperRecord diaper = new DiaperRecord();
        diaper.setId(1L); diaper.setRecordType(DiaperType.BOTH);
        diaper.setRecordTime(OffsetDateTime.parse("2026-09-09T01:00:00Z"));
        MilkStorageRecord milk = new MilkStorageRecord();
        milk.setId(1L); milk.setAmountMl(new BigDecimal("100"));
        milk.setStoredAt(OffsetDateTime.parse("2026-09-09T10:00:00+08:00"));
        WeightRecord weight = new WeightRecord();
        weight.setId(1L); weight.setWeightKg(new BigDecimal("3.5"));
        weight.setMeasuredAt(OffsetDateTime.parse("2026-09-09T03:00:00Z"));
        when(events.recordsBetween(eq(1L), any(), any())).thenReturn(Collections.singletonList(event));
        when(diapers.recordsBetween(eq(1L), any(), any())).thenReturn(Collections.singletonList(diaper));
        when(storage.recordsBetween(eq(1L), any(), any())).thenReturn(Collections.singletonList(milk));
        when(weights.recordsBetween(eq(1L), any(), any())).thenReturn(Collections.singletonList(weight));
        assertThat(service.get(from, to).getItems()).extracting(CalendarItemResponse::getKey)
                .containsExactly("EVENT:1", "DIAPER:1", "MILK_STORAGE:1", "WEIGHT:1");
    }

    private MilestoneResponse milestone(Long id, String code, String time) {
        return MilestoneResponse.builder().id(id).code(code).system(id == null).title(code)
                .targetTime(OffsetDateTime.parse(time)).build();
    }
}
