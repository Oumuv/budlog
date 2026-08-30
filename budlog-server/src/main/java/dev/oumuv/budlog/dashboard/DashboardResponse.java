package dev.oumuv.budlog.dashboard;

import dev.oumuv.budlog.baby.BabyResponse;
import dev.oumuv.budlog.diaper.DiaperResponse;
import dev.oumuv.budlog.feeding.FeedingResponse;
import dev.oumuv.budlog.milestone.MilestoneResponse;
import dev.oumuv.budlog.setting.SettingResponse;
import dev.oumuv.budlog.task.TaskResponse;
import dev.oumuv.budlog.timeline.TimelineItemResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class DashboardResponse {

    private boolean configured;
    private LocalDate date;
    private BabyResponse baby;
    private List<MilestoneResponse> milestones;
    private MilestoneResponse nextMilestone;
    private FeedingResponse lastFeeding;
    private Long lastFeedingMinutesAgo;
    private OffsetDateTime nextExpectedFeedingTime;
    private Long nextFeedingMinutesRemaining;
    private DiaperResponse lastPee;
    private Long lastPeeMinutesAgo;
    private DiaperResponse lastPoop;
    private Long lastPoopMinutesAgo;
    private DailySummaryResponse todaySummary;
    private List<TaskResponse> tasks;
    private List<TimelineItemResponse> recentTimeline;
    private SettingResponse settings;
}

