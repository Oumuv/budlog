package dev.oumuv.budlog.agent;

import dev.oumuv.budlog.agent.dto.AgentFeedingRecordResponse;
import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.PageResponse;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.config.AppProperties;
import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.diaper.DiaperRepository;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.feeding.FeedingRepository;
import dev.oumuv.budlog.milkstorage.MilkStorageRecord;
import dev.oumuv.budlog.milkstorage.MilkStorageRepository;
import dev.oumuv.budlog.weight.WeightRecord;
import dev.oumuv.budlog.weight.WeightRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Bounded, baby-scoped, soft-delete-aware read queries. No per-record service conversion/query. */
@Service
@Transactional(readOnly = true)
public class AgentReadService {
    private final BabyService babyService;
    private final TimeService timeService;
    private final AppProperties properties;
    private final FeedingRepository feedings;
    private final DiaperRepository diapers;
    private final MilkStorageRepository milk;
    private final WeightRepository weights;

    public AgentReadService(BabyService babyService, TimeService timeService, AppProperties properties,
                            FeedingRepository feedings, DiaperRepository diapers,
                            MilkStorageRepository milk, WeightRepository weights) {
        this.babyService = babyService;
        this.timeService = timeService;
        this.properties = properties;
        this.feedings = feedings;
        this.diapers = diapers;
        this.milk = milk;
        this.weights = weights;
    }

    public BabyProfile baby() { return babyService.requireCurrent(); }
    public ZoneId zone(BabyProfile baby) { return timeService.zone(baby.getTimezone()); }
    public OffsetDateTime start(LocalDate date, ZoneId zone) { return timeService.day(date, zone.getId()).getStart(); }
    public OffsetDateTime endExclusive(LocalDate date, ZoneId zone) { return timeService.day(date, zone.getId()).getEnd(); }

    public void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw BusinessException.validation("from 和 to 必须是有效的日期，且 from 不得晚于 to");
        }
        if (ChronoUnit.DAYS.between(from, to) >= properties.getAgent().getMaxRangeDays()) {
            throw BusinessException.validation("日期范围不能超过 " + properties.getAgent().getMaxRangeDays() + " 个自然日");
        }
        if (to.equals(LocalDate.MAX)) {
            throw BusinessException.validation("结束日期超出支持范围");
        }
    }

    public void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw BusinessException.validation("page 必须非负，size 必须在 1 到 100 之间，分页偏移不得溢出");
        }
    }

    public PageResponse<AgentFeedingRecordResponse> feedings(BabyProfile baby, LocalDate from, LocalDate to,
                                                               int page, int size) {
        validateRange(from, to);
        validatePage(page, size);
        ZoneId zone = zone(baby);
        Page<FeedingRecord> result = feedings
                .findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDescIdDesc(
                        baby.getId(), start(from, zone), endExclusive(to, zone), PageRequest.of(page, size));
        List<AgentFeedingRecordResponse> content = new ArrayList<AgentFeedingRecordResponse>();
        for (FeedingRecord record : result.getContent()) {
            content.add(new AgentFeedingRecordResponse(record));
        }
        return PageResponse.from(result, content);
    }

    public List<FeedingRecord> feedingsBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return feedings.findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDesc(babyId, from, to);
    }
    public FeedingRecord latestFeedingBefore(Long babyId, OffsetDateTime before) {
        return feedings.findFirstByBabyIdAndDeletedAtIsNullAndStartTimeLessThanOrderByStartTimeDescIdDesc(babyId, before).orElse(null);
    }
    public List<DiaperRecord> diapersBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return diapers.findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualAndRecordTimeLessThanOrderByRecordTimeDesc(babyId, from, to);
    }
    public List<MilkStorageRecord> milkBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return milk.findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualAndStoredAtLessThanOrderByStoredAtDesc(babyId, from, to);
    }
    public WeightRecord latestWeightBefore(Long babyId, OffsetDateTime before) {
        return weights.findFirstByBabyIdAndDeletedAtIsNullAndMeasuredAtLessThanOrderByMeasuredAtDescIdDesc(babyId, before).orElse(null);
    }
    public List<WeightRecord> weightsBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return weights.findAllByBabyIdAndDeletedAtIsNullAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtDesc(babyId, from, to);
    }
}
