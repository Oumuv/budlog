package dev.oumuv.budlog.calendar;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class CalendarResponse {
    private LocalDate from;
    private LocalDate to;
    private String timezone;
    private List<CalendarItemResponse> items;
}
