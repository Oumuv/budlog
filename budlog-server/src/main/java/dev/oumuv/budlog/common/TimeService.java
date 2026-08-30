package dev.oumuv.budlog.common;

import dev.oumuv.budlog.config.AppProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class TimeService {

    private final Clock clock;
    private final AppProperties properties;

    public TimeService(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public ZoneId zone(String zoneId) {
        String resolved = zoneId == null || zoneId.trim().isEmpty() ? properties.getTimezone() : zoneId;
        try {
            return ZoneId.of(resolved);
        } catch (Exception exception) {
            throw BusinessException.validation("不支持的时区：" + resolved);
        }
    }

    public LocalDate today(String zoneId) {
        return ZonedDateTime.now(clock.withZone(zone(zoneId))).toLocalDate();
    }

    public TimeRange day(LocalDate date, String zoneId) {
        ZoneId zone = zone(zoneId);
        return new TimeRange(
                date.atStartOfDay(zone).toOffsetDateTime(),
                date.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
        );
    }
}

