package dev.oumuv.budlog.agent;

import dev.oumuv.budlog.agent.dto.AgentCareDailyResponse;
import dev.oumuv.budlog.agent.dto.AgentDailyFeedingResponse;
import dev.oumuv.budlog.agent.dto.AgentDailyReportResponse;
import dev.oumuv.budlog.agent.dto.AgentFeedingRecordResponse;
import dev.oumuv.budlog.agent.dto.AgentFeedingTrendsResponse;
import dev.oumuv.budlog.agent.dto.AgentProfileResponse;
import dev.oumuv.budlog.agent.dto.AgentWeightTrendsResponse;
import dev.oumuv.budlog.common.ApiResponse;
import dev.oumuv.budlog.common.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/agent/v1")
public class AgentController {
    private final AgentReadService read;
    private final AgentAnalyticsService analytics;

    public AgentController(AgentReadService read, AgentAnalyticsService analytics) {
        this.read = read;
        this.analytics = analytics;
    }

    @GetMapping("/profile")
    public ApiResponse<AgentProfileResponse> profile() { return ApiResponse.success(analytics.profile()); }

    @GetMapping("/feedings")
    public ApiResponse<PageResponse<AgentFeedingRecordResponse>> feedings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(read.feedings(read.baby(), from, to, page, size));
    }

    @GetMapping("/feedings/daily")
    public ApiResponse<List<AgentDailyFeedingResponse>> feedingDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(analytics.feedingDaily(from, to));
    }

    @GetMapping("/feedings/trends")
    public ApiResponse<AgentFeedingTrendsResponse> feedingTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(analytics.feedingTrends(days, endDate));
    }

    @GetMapping("/care/daily")
    public ApiResponse<AgentCareDailyResponse> careDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(analytics.careDaily(date));
    }

    @GetMapping("/daily-report")
    public ApiResponse<AgentDailyReportResponse> dailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(analytics.dailyReport(date));
    }

    @GetMapping("/weights/trends")
    public ApiResponse<AgentWeightTrendsResponse> weightTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(analytics.weightTrends(from, to));
    }
}
