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
import dev.oumuv.budlog.task.TaskResponse;
import dev.oumuv.budlog.task.TaskService;
import dev.oumuv.budlog.task.TaskStatus;
import dev.oumuv.budlog.timeline.TimelineItemResponse;
import dev.oumuv.budlog.timeline.TimelineResponse;
import dev.oumuv.budlog.timeline.TimelineService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private final SettingService settingService;
    private final TimeService timeService;

    public DashboardService(
            BabyService babyService,
            MilestoneService milestoneService,
            FeedingService feedingService,
            DiaperService diaperService,
            TaskService taskService,
            TimelineService timelineService,
            SettingService settingService,
            TimeService timeService) {
        this.babyService = babyService;
        this.milestoneService = milestoneService;
        this.feedingService = feedingService;
        this.diaperService = diaperService;
        this.taskService = taskService;
        this.timelineService = timelineService;
        this.settingService = settingService;
        this.timeService = timeService;
    }

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
        TimelineResponse timeline = timelineService.get(date);
        List<MilestoneResponse> milestones = milestoneService.list();
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

        List<TaskResponse> openTasks = taskService.list(TaskStatus.TODO, null, null);
        if (openTasks.size() > 6) {
            openTasks = new ArrayList<TaskResponse>(openTasks.subList(0, 6));
        }
        List<TimelineItemResponse> recent = timeline.getItems();
        if (recent.size() > 8) {
            recent = new ArrayList<TimelineItemResponse>(recent.subList(0, 8));
        }

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
                .todaySummary(timeline.getSummary())
                .tasks(openTasks)
                .recentTimeline(recent)
                .settings(settings)
                .build();
    }

    private Long minutesAgo(OffsetDateTime time, OffsetDateTime now) {
        return time == null ? null : Math.max(0, Duration.between(time, now).toMinutes());
    }
}

