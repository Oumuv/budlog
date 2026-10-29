package dev.oumuv.budlog.agent;

import dev.oumuv.budlog.agent.dto.AgentDailyFeedingResponse;
import dev.oumuv.budlog.agent.dto.AgentFeedingTrendsResponse;
import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.config.AppProperties;
import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.diaper.DiaperType;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAnalyticsServiceTest {
    private AgentReadService read;
    private AgentAnalyticsService analytics;
    private BabyProfile baby;

    @BeforeEach
    void setUp() {
        read = mock(AgentReadService.class);
        baby = new BabyProfile();
        baby.setId(1L);
        baby.setBirthTime(OffsetDateTime.parse("2026-01-01T08:00:00+08:00"));
        baby.setTimezone("Asia/Shanghai");
        when(read.baby()).thenReturn(baby);
        when(read.zone(baby)).thenReturn(java.time.ZoneId.of("Asia/Shanghai"));
        when(read.start(any(), any())).thenAnswer(invocation -> ((LocalDate) invocation.getArgument(0))
                .atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toOffsetDateTime());
        when(read.endExclusive(any(), any())).thenAnswer(invocation -> ((LocalDate) invocation.getArgument(0))
                .plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toOffsetDateTime());
        AppProperties properties = new AppProperties();
        analytics = new AgentAnalyticsService(read,
                new TimeService(Clock.fixed(OffsetDateTime.parse("2026-01-10T08:00:00+08:00").toInstant(), ZoneOffset.UTC), properties));
    }

    @Test
    void mergesExactStartButNotAdjacentMinutesAndAssignsCrossMidnightIntervalToLaterDay() {
        LocalDate first = LocalDate.parse("2026-01-02");
        OffsetDateTime t1 = OffsetDateTime.parse("2026-01-02T23:00:00+08:00");
        OffsetDateTime t2 = OffsetDateTime.parse("2026-01-03T03:00:00+08:00");
        when(read.feedingsBetween(eq(1L), any(), any())).thenReturn(Arrays.asList(
                feeding(1, FeedingType.BREAST_BOTTLE, t1, "80.0", null),
                feeding(2, FeedingType.FORMULA_BOTTLE, t1, "40.0", null),
                feeding(3, FeedingType.BREAST_DIRECT, t2, null, t2.plusMinutes(15)),
                feeding(4, FeedingType.BREAST_BOTTLE, t2.plusMinutes(1), "20.0", null)));
        AgentDailyFeedingResponse day1 = analytics.feedingDaily(first, first.plusDays(1)).get(0);
        AgentDailyFeedingResponse day2 = analytics.feedingDaily(first, first.plusDays(1)).get(1);
        assertThat(day1.getRecordCount()).isEqualTo(2);
        assertThat(day1.getSessionCount()).isEqualTo(1);
        assertThat(day1.getMeasuredBottleIntakeMl()).isEqualByComparingTo("120.0");
        assertThat(day2.getSessionCount()).isEqualTo(2);
        assertThat(day2.getAverageIntervalMinutes()).isEqualTo(121L);
        assertThat(day2.getLongestRecordedIntervalMinutes()).isEqualTo(240L);
        assertThat(day2.getDirectFeedingMinutes()).isEqualTo(15L);
        assertThat(day2.isTotalIntakeKnown()).isFalse();
    }

    @Test
    void missingBottleAmountDoesNotBecomeKnownTotalOrFalseTrendPercentage() {
        OffsetDateTime time = OffsetDateTime.parse("2026-01-08T09:00:00+08:00");
        when(read.feedingsBetween(eq(1L), any(), any())).thenReturn(Collections.singletonList(
                feeding(1, FeedingType.BREAST_BOTTLE, time, null, null)));
        AgentDailyFeedingResponse day = analytics.feedingDaily(LocalDate.parse("2026-01-08"), LocalDate.parse("2026-01-08")).get(0);
        assertThat(day.isMeasuredBottleIntakeKnown()).isFalse();
        assertThat(day.isTotalIntakeKnown()).isFalse();
        AgentFeedingTrendsResponse trend = analytics.feedingTrends(7, LocalDate.parse("2026-01-09"));
        assertThat(trend.getMeasuredBottleChangePercent()).isNull();
    }

    @Test
    void emptyDayDoesNotClaimIntakeKnown() {
        when(read.feedingsBetween(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        AgentDailyFeedingResponse day = analytics.feedingDaily(LocalDate.parse("2026-01-04"), LocalDate.parse("2026-01-04")).get(0);
        assertThat(day.getRecordCount()).isZero();
        assertThat(day.isTotalIntakeKnown()).isFalse();
        assertThat(day.isMeasuredBottleIntakeKnown()).isFalse();
    }

    @Test
    void bothDiaperCountsAsOnePeeAndOnePoop() {
        OffsetDateTime time = OffsetDateTime.parse("2026-01-05T09:00:00+08:00");
        DiaperRecord both = new DiaperRecord();
        both.setRecordType(DiaperType.BOTH);
        when(read.diapersBetween(eq(1L), any(), any())).thenReturn(Collections.singletonList(both));
        when(read.milkBetween(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        assertThat(analytics.careDaily(LocalDate.parse("2026-01-05")).getDiaper().getPeeRecordCount()).isEqualTo(1);
        assertThat(analytics.careDaily(LocalDate.parse("2026-01-05")).getDiaper().getPoopRecordCount()).isEqualTo(1);
    }

    private FeedingRecord feeding(long id, FeedingType type, OffsetDateTime start, String amount, OffsetDateTime end) {
        FeedingRecord record = new FeedingRecord();
        record.setId(id);
        record.setFeedingType(type);
        record.setStartTime(start);
        record.setEndTime(end);
        if (amount != null) record.setAmountMl(new BigDecimal(amount));
        return record;
    }
}
