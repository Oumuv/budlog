package dev.oumuv.budlog.common;

import java.time.OffsetDateTime;

public class TimeRange {

    private final OffsetDateTime start;
    private final OffsetDateTime end;

    public TimeRange(OffsetDateTime start, OffsetDateTime end) {
        this.start = start;
        this.end = end;
    }

    public OffsetDateTime getStart() {
        return start;
    }

    public OffsetDateTime getEnd() {
        return end;
    }
}

