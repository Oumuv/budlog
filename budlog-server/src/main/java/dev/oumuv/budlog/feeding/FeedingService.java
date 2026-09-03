package dev.oumuv.budlog.feeding;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.PageResponse;
import dev.oumuv.budlog.common.TimeService;
import dev.oumuv.budlog.setting.SettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FeedingService {

    private final FeedingRepository repository;
    private final BabyService babyService;
    private final SettingService settingService;
    private final TimeService timeService;

    public FeedingService(
            FeedingRepository repository,
            BabyService babyService,
            SettingService settingService,
            TimeService timeService) {
        this.repository = repository;
        this.babyService = babyService;
        this.settingService = settingService;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedingResponse> list(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        BabyProfile baby = babyService.requireCurrent();
        validateRangeAndPage(from, to, page, size);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<FeedingRecord> records;
        if (from != null && to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDesc(
                    baby.getId(), from, to, pageable);
        } else if (from != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualOrderByStartTimeDesc(
                    baby.getId(), from, pageable);
        } else if (to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndStartTimeLessThanOrderByStartTimeDesc(
                    baby.getId(), to, pageable);
        } else {
            records = repository.findAllByBabyIdAndDeletedAtIsNullOrderByStartTimeDesc(baby.getId(), pageable);
        }
        List<FeedingResponse> content = new ArrayList<FeedingResponse>();
        for (FeedingRecord record : records.getContent()) {
            content.add(toResponse(record));
        }
        return PageResponse.from(records, content);
    }

    @Transactional(readOnly = true)
    public FeedingResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public FeedingResponse create(FeedingRequest request) {
        FeedingRecord duplicate = repository.findByClientRequestId(request.getClientRequestId()).orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate);
        }

        FeedingRecord record = new FeedingRecord();
        record.setBaby(babyService.requireCurrent());
        record.setClientRequestId(request.getClientRequestId());
        apply(record, request);
        return toResponse(repository.save(record));
    }

    @Transactional
    public FeedingResponse update(Long id, FeedingRequest request) {
        FeedingRecord record = require(id);
        apply(record, request);
        return toResponse(repository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        FeedingRecord record = require(id);
        record.setDeletedAt(timeService.now());
        repository.save(record);
    }

    @Transactional(readOnly = true)
    public List<FeedingRecord> recordsBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return repository.findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDesc(
                babyId, from, to);
    }

    @Transactional(readOnly = true)
    public FeedingRecord latest(Long babyId) {
        return repository.findFirstByBabyIdAndDeletedAtIsNullOrderByStartTimeDesc(babyId).orElse(null);
    }

    @Transactional(readOnly = true)
    public FeedingRecord latestBefore(Long babyId, OffsetDateTime startTime) {
        return repository.findFirstByBabyIdAndDeletedAtIsNullAndStartTimeLessThanOrderByStartTimeDesc(
                babyId, startTime).orElse(null);
    }

    public FeedingResponse toResponse(FeedingRecord record) {
        FeedingRecord previous = repository
                .findFirstByBabyIdAndDeletedAtIsNullAndStartTimeLessThanOrderByStartTimeDesc(
                        record.getBaby().getId(), record.getStartTime())
                .orElse(null);
        Long duration = record.getEndTime() == null
                ? null
                : Math.max(0, Duration.between(record.getStartTime(), record.getEndTime()).toMinutes());
        Long interval = previous == null
                ? null
                : Math.max(0, Duration.between(previous.getStartTime(), record.getStartTime()).toMinutes());
        int defaultInterval = settingService.requireSetting().getDefaultFeedingIntervalMin();

        return FeedingResponse.builder()
                .id(record.getId())
                .clientRequestId(record.getClientRequestId())
                .feedingType(record.getFeedingType())
                .breastSide(record.getBreastSide())
                .startTime(record.getStartTime())
                .endTime(record.getEndTime())
                .amountMl(record.getAmountMl())
                .note(record.getNote())
                .durationMinutes(duration)
                .minutesSincePrevious(interval)
                .nextExpectedTime(record.getStartTime().plusMinutes(defaultInterval))
                .build();
    }

    private void apply(FeedingRecord record, FeedingRequest request) {
        validate(request);
        record.setFeedingType(request.getFeedingType());
        record.setStartTime(request.getStartTime());
        record.setEndTime(request.getEndTime());
        record.setAmountMl(request.getAmountMl());
        record.setBreastSide(request.getFeedingType() == FeedingType.FORMULA_BOTTLE ? null : request.getBreastSide());
        record.setNote(normalize(request.getNote()));
    }

    private void validate(FeedingRequest request) {
        if (request.getEndTime() != null && request.getEndTime().isBefore(request.getStartTime())) {
            throw BusinessException.validation("结束时间不能早于开始时间");
        }
        if (request.getFeedingType() == FeedingType.BREAST_DIRECT && request.getBreastSide() == null) {
            throw BusinessException.validation("亲喂必须选择侧别");
        }
        if (request.getFeedingType() != FeedingType.BREAST_DIRECT) {
            BigDecimal amount = request.getAmountMl();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw BusinessException.validation("瓶喂奶量必须大于 0");
            }
        }
    }

    private void validateRangeAndPage(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        if (page < 0 || size < 1) {
            throw BusinessException.validation("分页参数必须为正数");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw BusinessException.validation("开始时间必须早于结束时间");
        }
    }

    private FeedingRecord require(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> BusinessException.notFound("喂奶记录不存在或已删除"));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
