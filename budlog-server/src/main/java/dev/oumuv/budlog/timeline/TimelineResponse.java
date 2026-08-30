package dev.oumuv.budlog.timeline;

import dev.oumuv.budlog.dashboard.DailySummaryResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class TimelineResponse {

    private LocalDate date;
    private DailySummaryResponse summary;
    private List<TimelineItemResponse> items;
}

