package dev.oumuv.budlog.dashboard;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.diaper.DiaperResponse;
import dev.oumuv.budlog.diaper.DiaperService;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingResponse;
import dev.oumuv.budlog.feeding.FeedingService;
import dev.oumuv.budlog.milestone.MilestoneResponse;
import dev.oumuv.budlog.milestone.MilestoneService;
import dev.oumuv.budlog.setting.SettingResponse;
import dev.oumuv.budlog.setting.SettingService;
import dev.oumuv.budlog.summary.SummaryService;
import dev.oumuv.budlog.task.TaskResponse;
import dev.oumuv.budlog.task.TaskService;
import dev.oumuv.budlog.task.TaskStatus;
import dev.oumuv.budlog.timeline.TimelineItemResponse;
import dev.oumuv.budlog.timeline.TimelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class DashboardService {

    private final BabyService babyService;
    private final MilestoneService milestoneService;
    private final FeedingService feedingService;
    private final DiaperService diaperService;
    private final TaskService taskService;
    private final TimelineService timelineService;
    private final SummaryService summaryService;
    private final SettingService settingService;
    private final TimeService timeService;

    public DashboardService(
            BabyService babyService,
            MilestoneService milestoneService,
            FeedingService feedingService,
            DiaperService diaperService,
            TaskService taskService,
            TimelineService timelineService,
            SummaryService summaryService,
            SettingService settingService,
            TimeService timeService) {
        this.babyService = babyService;
        this.milestoneService = milestoneService;
        this.feedingService = feedingService;
        this.diaperService = diaperService;
        this.taskService = taskService;
        this.timelineService = timelineService;
        this.summaryService = summaryService;
        this.settingService = settingService;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResponse get(LocalDate requestedDate) {
        SettingResponse settings = settingService.get();
        BabyProfile baby = babyService.findCurrent().orElse(null);
        if (baby == null) {
            return DashboardResponse.builder()
                    .configured(false)
                    .date(requestedDate)
                    .milestones(Collections.<MilestoneResponse>emptyList())
                    .todaySummary(DailySummaryResponse.empty())
                    .tasks(Collections.<TaskResponse>emptyList())
                    .recentTimeline(Collections.<TimelineItemResponse>emptyList())
                    .settings(settings)
                    .build();
        }

        LocalDate date = requestedDate == null ? timeService.today(baby.getTimezone()) : requestedDate;
        List<TimelineItemResponse> recent = timelineService.recent(baby, date, 8);
        DailySummaryResponse summary = summaryService.daily(baby, date);
        List<MilestoneResponse> milestones = milestoneService.list(baby);
        MilestoneResponse next = null;
        for (MilestoneResponse milestone : milestones) {
            if (milestone.getDaysDifference() >= 0) {
                next = milestone;
                break;
            }
        }

        FeedingRecord latestFeeding = feedingService.latest(baby.getId());
        FeedingResponse lastFeeding = latestFeeding == null ? null : feedingService.toResponse(latestFeeding);
        DiaperRecord pee = diaperService.lastPee(baby.getId());
        DiaperRecord poop = diaperService.lastPoop(baby.getId());
        OffsetDateTime now = timeService.now();

        List<TaskResponse> openTasks = taskService.listForBaby(baby.getId(), TaskStatus.TODO, null, null, 6);

        return DashboardResponse.builder()
                .configured(true)
                .date(date)
                .baby(babyService.toResponse(baby))
                .milestones(milestones)
                .nextMilestone(next)
                .lastFeeding(lastFeeding)
                .lastFeedingMinutesAgo(minutesAgo(latestFeeding == null ? null : latestFeeding.getStartTime(), now))
                .nextExpectedFeedingTime(lastFeeding == null ? null : lastFeeding.getNextExpectedTime())
                .nextFeedingMinutesRemaining(lastFeeding == null
                        ? null
                        : Duration.between(now, lastFeeding.getNextExpectedTime()).toMinutes())
                .lastPee(diaperService.toResponse(pee))
                .lastPeeMinutesAgo(minutesAgo(pee == null ? null : pee.getRecordTime(), now))
                .lastPoop(diaperService.toResponse(poop))
                .lastPoopMinutesAgo(minutesAgo(poop == null ? null : poop.getRecordTime(), now))
                .todaySummary(summary)
                .tasks(openTasks)
                .recentTimeline(recent)
                .settings(settings)
                .build();
    }

    private Long minutesAgo(OffsetDateTime time, OffsetDateTime now) {
        return time == null ? null : Math.max(0, Duration.between(time, now).toMinutes());
    }
}

