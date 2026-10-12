package dev.oumuv.budlog.agent;

import dev.oumuv.budlog.agent.dto.AgentCareDailyResponse;
import dev.oumuv.budlog.agent.dto.AgentDailyFeedingResponse;
import dev.oumuv.budlog.agent.dto.AgentDailyReportResponse;
import dev.oumuv.budlog.agent.dto.AgentDataQualityResponse;
import dev.oumuv.budlog.agent.dto.AgentFeedingTrendsResponse;
import dev.oumuv.budlog.agent.dto.AgentProfileResponse;
import dev.oumuv.budlog.agent.dto.AgentWeightTrendsResponse;
import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.diaper.DiaperType;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingType;
import dev.oumuv.budlog.milkstorage.MilkStorageRecord;
import dev.oumuv.budlog.weight.WeightRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AgentAnalyticsService {
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal MILLIS_PER_DAY = new BigDecimal("86400000");
    private final AgentReadService read;
    private final TimeService time;

    public AgentAnalyticsService(AgentReadService read, TimeService time) {
        this.read = read;
        this.time = time;
    }

    public AgentProfileResponse profile() {
        return profile(read.baby(), time.now());
    }

    private AgentProfileResponse profile(BabyProfile baby, OffsetDateTime asOf) {
        // Completed 24-hour periods, not the existing one-based calendar-day ageDayNumber.
        return new AgentProfileResponse(baby.getName(), baby.getBirthTime(), baby.getTimezone(),
                Math.max(0, Duration.between(baby.getBirthTime(), asOf).toDays()));
    }

    public List<AgentDailyFeedingResponse> feedingDaily(LocalDate from, LocalDate to) {
        read.validateRange(from, to);
        BabyProfile baby = read.baby();
        return feedingDaily(baby, from, to);
    }

    private List<AgentDailyFeedingResponse> feedingDaily(BabyProfile baby, LocalDate from, LocalDate to) {
        ZoneId zone = read.zone(baby);
        OffsetDateTime start = read.start(from, zone);
        List<FeedingRecord> records = read.feedingsBetween(baby.getId(), start, read.endExclusive(to, zone));
        // Existing repository sorts by time only. Sort deterministically and group exact instants.
        Collections.sort(records, Comparator.comparing(FeedingRecord::getStartTime).thenComparing(FeedingRecord::getId));
        Map<LocalDate, AgentDailyFeedingResponse> daily = new LinkedHashMap<LocalDate, AgentDailyFeedingResponse>();
        Map<LocalDate, Long> intervalSums = new LinkedHashMap<LocalDate, Long>();
        Map<LocalDate, Integer> intervalCounts = new LinkedHashMap<LocalDate, Integer>();
        Map<LocalDate, Integer> bottleCounts = new LinkedHashMap<LocalDate, Integer>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            AgentDailyFeedingResponse response = new AgentDailyFeedingResponse();
            response.setDate(day);
            response.setDirectFeedingMinutes(0L);
            daily.put(day, response);
        }
        FeedingRecord predecessor = read.latestFeedingBefore(baby.getId(), start);
        OffsetDateTime previousSession = predecessor == null ? null : predecessor.getStartTime();
        OffsetDateTime currentSession = null;
        for (FeedingRecord record : records) {
            LocalDate day = record.getStartTime().atZoneSameInstant(zone).toLocalDate();
            AgentDailyFeedingResponse row = daily.get(day);
            row.setRecordCount(row.getRecordCount() + 1);
            if (currentSession == null || !record.getStartTime().isEqual(currentSession)) {
                row.setSessionCount(row.getSessionCount() + 1);
                if (previousSession != null) {
                    long minutes = Duration.between(previousSession, record.getStartTime()).toMinutes();
                    if (minutes > 0) {
                        intervalSums.put(day, intervalSums.getOrDefault(day, 0L) + minutes);
                        intervalCounts.put(day, intervalCounts.getOrDefault(day, 0) + 1);
                        row.setLongestRecordedIntervalMinutes(Math.max(
                                row.getLongestRecordedIntervalMinutes() == null ? 0 : row.getLongestRecordedIntervalMinutes(), minutes));
                    }
                }
                previousSession = record.getStartTime();
                currentSession = record.getStartTime();
            }
            if (row.getLastFeedingAt() == null || record.getStartTime().isAfter(row.getLastFeedingAt())) {
                row.setLastFeedingAt(record.getStartTime());
            }
            BigDecimal amount = record.getAmountMl();
            if (record.getFeedingType() == FeedingType.BREAST_BOTTLE) {
                row.setBreastBottleRecordCount(row.getBreastBottleRecordCount() + 1);
                if (amount != null) {
                    row.setBreastBottleMl(row.getBreastBottleMl().add(amount));
                    bottleCounts.put(day, bottleCounts.getOrDefault(day, 0) + 1);
                } else {
                    row.setMeasuredBottleIntakeKnown(false);
                    row.setTotalIntakeKnown(false);
                }
            } else if (record.getFeedingType() == FeedingType.FORMULA_BOTTLE) {
                row.setFormulaBottleRecordCount(row.getFormulaBottleRecordCount() + 1);
                if (amount != null) {
                    row.setFormulaBottleMl(row.getFormulaBottleMl().add(amount));
                    bottleCounts.put(day, bottleCounts.getOrDefault(day, 0) + 1);
                } else {
                    row.setMeasuredBottleIntakeKnown(false);
                    row.setTotalIntakeKnown(false);
                }
            } else if (record.getFeedingType() == FeedingType.BREAST_DIRECT) {
                row.setDirectFeedingRecordCount(row.getDirectFeedingRecordCount() + 1);
                row.setTotalIntakeKnown(false);
                if (record.getEndTime() == null || record.getEndTime().isBefore(record.getStartTime())) {
                    row.setDirectFeedingMinutes(null);
                } else if (row.getDirectFeedingMinutes() != null) {
                    row.setDirectFeedingMinutes(row.getDirectFeedingMinutes()
                            + Duration.between(record.getStartTime(), record.getEndTime()).toMinutes());
                }
            }
        }
        for (AgentDailyFeedingResponse row : daily.values()) {
            if (row.getRecordCount() == 0) {
                // Zero recorded events is not evidence of zero actual feeding.
                row.setTotalIntakeKnown(false);
                row.setMeasuredBottleIntakeKnown(false);
            }
            LocalDate day = row.getDate();
            row.setMeasuredBottleIntakeMl(row.getBreastBottleMl().add(row.getFormulaBottleMl()).setScale(1, RoundingMode.HALF_UP));
            int count = bottleCounts.getOrDefault(day, 0);
            if (count > 0 && row.isMeasuredBottleIntakeKnown()) {
                row.setAverageBottleMl(row.getMeasuredBottleIntakeMl().divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP));
            }
            int intervals = intervalCounts.getOrDefault(day, 0);
            if (intervals > 0) {
                row.setAverageIntervalMinutes(BigDecimal.valueOf(intervalSums.get(day))
                        .divide(BigDecimal.valueOf(intervals), 0, RoundingMode.HALF_UP).longValue());
            }
        }
        return new ArrayList<AgentDailyFeedingResponse>(daily.values());
    }

    public AgentFeedingTrendsResponse feedingTrends(int days, LocalDate endDate) {
        if (days != 7 && days != 30) {
            throw BusinessException.validation("days 仅支持 7 或 30");
        }
        BabyProfile baby = read.baby();
        LocalDate end = endDate == null ? time.today(baby.getTimezone()).minusDays(1) : endDate;
        LocalDate previousFrom;
        try {
            previousFrom = end.minusDays(days * 2L - 1);
        } catch (RuntimeException exception) {
            throw BusinessException.validation("endDate 超出支持范围");
        }
        read.validateRange(previousFrom, end);
        List<AgentDailyFeedingResponse> all = feedingDaily(baby, previousFrom, end);
        AgentFeedingTrendsResponse result = new AgentFeedingTrendsResponse();
        result.setDays(days);
        result.setEndDate(end);
        result.setPreviousFrom(previousFrom);
        result.setPreviousTo(end.minusDays(days));
        result.setDaily(new ArrayList<AgentDailyFeedingResponse>(all.subList(days, all.size())));
        BigDecimal currentMl = BigDecimal.ZERO.setScale(1);
        BigDecimal previousMl = BigDecimal.ZERO.setScale(1);
        int currentSessions = 0;
        int previousSessions = 0;
        boolean currentKnown = true;
        boolean previousKnown = true;
        boolean currentBottleKnown = true;
        boolean previousBottleKnown = true;
        for (int i = 0; i < all.size(); i++) {
            AgentDailyFeedingResponse day = all.get(i);
            if (i < days) {
                previousMl = previousMl.add(day.getMeasuredBottleIntakeMl());
                previousSessions += day.getSessionCount();
                previousKnown &= day.isTotalIntakeKnown();
                previousBottleKnown &= day.isMeasuredBottleIntakeKnown();
            } else {
                currentMl = currentMl.add(day.getMeasuredBottleIntakeMl());
                currentSessions += day.getSessionCount();
                currentKnown &= day.isTotalIntakeKnown();
                currentBottleKnown &= day.isMeasuredBottleIntakeKnown();
            }
        }
        result.setMeasuredBottleIntakeMl(currentMl);
        result.setPreviousMeasuredBottleIntakeMl(previousMl);
        result.setSessionCount(currentSessions);
        result.setPreviousSessionCount(previousSessions);
        result.setTotalIntakeKnown(currentKnown);
        result.setPreviousTotalIntakeKnown(previousKnown);
        result.setMeasuredBottleIntakeKnown(currentBottleKnown);
        result.setPreviousMeasuredBottleIntakeKnown(previousBottleKnown);
        // Percentage describes *measured bottle intake only*. No percent if baseline is zero/unrecorded or bottle amounts missing.
        LocalDate birthDate = baby.getBirthTime().atZoneSameInstant(read.zone(baby)).toLocalDate();
        result.setComparisonAvailable(!previousFrom.isBefore(birthDate) && previousSessions > 0 && currentSessions > 0
                && previousBottleKnown && currentBottleKnown && previousMl.signum() > 0);
        if (result.isComparisonAvailable()) {
            result.setMeasuredBottleChangePercent(currentMl.subtract(previousMl)
                    .multiply(BigDecimal.valueOf(100)).divide(previousMl, 1, RoundingMode.HALF_UP));
        }
        return result;
    }

    public AgentCareDailyResponse careDaily(LocalDate date) {
        if (date == null) { throw BusinessException.validation("date 不能为空"); }
        BabyProfile baby = read.baby();
        return careDaily(baby, date);
    }

    private AgentCareDailyResponse careDaily(BabyProfile baby, LocalDate date) {
        read.validateRange(date, date);
        ZoneId zone = read.zone(baby);
        OffsetDateTime start = read.start(date, zone);
        OffsetDateTime end = read.endExclusive(date, zone);
        AgentCareDailyResponse care = new AgentCareDailyResponse();
        care.setDate(date);
        AgentCareDailyResponse.Diaper diaper = new AgentCareDailyResponse.Diaper();
        for (DiaperRecord record : read.diapersBetween(baby.getId(), start, end)) {
            diaper.setRecordCount(diaper.getRecordCount() + 1);
            if (record.getRecordType() == DiaperType.PEE || record.getRecordType() == DiaperType.BOTH) {
                diaper.setPeeRecordCount(diaper.getPeeRecordCount() + 1);
            }
            if (record.getRecordType() == DiaperType.POOP || record.getRecordType() == DiaperType.BOTH) {
                diaper.setPoopRecordCount(diaper.getPoopRecordCount() + 1);
            }
        }
        care.setDiaper(diaper);
        AgentCareDailyResponse.MilkStorage storage = new AgentCareDailyResponse.MilkStorage();
        for (MilkStorageRecord record : read.milkBetween(baby.getId(), start, end)) {
            storage.setRecordCount(storage.getRecordCount() + 1);
            if (record.getAmountMl() != null) { storage.setAddedMl(storage.getAddedMl().add(record.getAmountMl())); }
        }
        care.setMilkStorage(storage);
        WeightRecord latest = read.latestWeightBefore(baby.getId(), end);
        AgentCareDailyResponse.Weight weight = new AgentCareDailyResponse.Weight();
        if (latest != null) {
            weight.setLatestKg(latest.getWeightKg());
            weight.setMeasuredAt(latest.getMeasuredAt());
            weight.setAgeOfMeasurementDays(Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(
                    latest.getMeasuredAt().atZoneSameInstant(zone).toLocalDate(), date)));
        }
        care.setWeight(weight);
        return care;
    }

    public AgentDailyReportResponse dailyReport(LocalDate date) {
        if (date == null) { throw BusinessException.validation("date 不能为空"); }
        BabyProfile baby = read.baby();
        AgentCareDailyResponse care = careDaily(baby, date);
        AgentDailyReportResponse report = new AgentDailyReportResponse();
        report.setDate(date);
        report.setTimezone(baby.getTimezone());
        report.setBaby(profile(baby, read.endExclusive(date, read.zone(baby))));
        report.setFeeding(feedingDaily(baby, date, date).get(0));
        report.setDiaper(care.getDiaper());
        report.setMilkStorage(care.getMilkStorage());
        report.setWeight(care.getWeight());
        report.setDataQuality(new AgentDataQualityResponse());
        report.setGeneratedAt(time.now().atZoneSameInstant(read.zone(baby)).toOffsetDateTime());
        return report;
    }

    public AgentWeightTrendsResponse weightTrends(LocalDate from, LocalDate to) {
        read.validateRange(from, to);
        BabyProfile baby = read.baby();
        ZoneId zone = read.zone(baby);
        List<WeightRecord> records = read.weightsBetween(baby.getId(), read.start(from, zone), read.endExclusive(to, zone));
        Collections.sort(records, Comparator.comparing(WeightRecord::getMeasuredAt).thenComparing(WeightRecord::getId));
        AgentWeightTrendsResponse response = new AgentWeightTrendsResponse();
        response.setFrom(from);
        response.setTo(to);
        List<AgentWeightTrendsResponse.Point> points = new ArrayList<AgentWeightTrendsResponse.Point>();
        WeightRecord previous = null;
        for (WeightRecord record : records) {
            AgentWeightTrendsResponse.Point point = new AgentWeightTrendsResponse.Point();
            point.setMeasuredAt(record.getMeasuredAt());
            point.setWeightKg(record.getWeightKg());
            if (previous != null) {
                BigDecimal delta = record.getWeightKg().subtract(previous.getWeightKg()).multiply(THOUSAND);
                point.setDeltaGrams(delta.setScale(1, RoundingMode.HALF_UP));
                long millis = Duration.between(previous.getMeasuredAt(), record.getMeasuredAt()).toMillis();
                if (millis > 0) {
                    point.setGramsPerDay(delta.multiply(MILLIS_PER_DAY)
                            .divide(BigDecimal.valueOf(millis), 1, RoundingMode.HALF_UP));
                }
            }
            points.add(point);
            previous = record;
        }
        response.setMeasurements(points);
        return response;
    }
}
