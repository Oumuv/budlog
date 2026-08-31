package dev.oumuv.budlog.weight;

import dev.oumuv.budlog.baby.BabyProfile;
import dev.oumuv.budlog.baby.BabyService;
import dev.oumuv.budlog.common.BusinessException;
import dev.oumuv.budlog.common.PageResponse;
import dev.oumuv.budlog.common.TimeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeightService {

    private static final BigDecimal MIN_WEIGHT_KG = new BigDecimal("0.100");
    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("100.000");

    private final WeightRepository repository;
    private final BabyService babyService;
    private final TimeService timeService;

    public WeightService(WeightRepository repository, BabyService babyService, TimeService timeService) {
        this.repository = repository;
        this.babyService = babyService;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<WeightResponse> list(LocalDate from, LocalDate to, int page, int size) {
        BabyProfile baby = babyService.requireCurrent();
        validateRangeAndPage(from, to, page, size);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<WeightRecord> records;
        if (from != null && to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndRecordDateGreaterThanEqualAndRecordDateLessThanOrderByRecordDateDescMeasuredAtDesc(
                    baby.getId(), from, to, pageable);
        } else if (from != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndRecordDateGreaterThanEqualOrderByRecordDateDescMeasuredAtDesc(
                    baby.getId(), from, pageable);
        } else if (to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndRecordDateLessThanOrderByRecordDateDescMeasuredAtDesc(
                    baby.getId(), to, pageable);
        } else {
            records = repository.findAllByBabyIdAndDeletedAtIsNullOrderByRecordDateDescMeasuredAtDesc(
                    baby.getId(), pageable);
        }
        List<WeightResponse> content = new ArrayList<WeightResponse>();
        for (WeightRecord record : records.getContent()) {
            content.add(toResponse(record));
        }
        return PageResponse.from(records, content);
    }

    @Transactional(readOnly = true)
    public WeightResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public WeightResponse create(WeightRequest request) {
        WeightRecord duplicate = repository.findByClientRequestId(request.getClientRequestId()).orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate);
        }
        BabyProfile baby = babyService.requireCurrent();
        WeightRecord record = new WeightRecord();
        record.setBaby(baby);
        record.setClientRequestId(request.getClientRequestId());
        apply(record, request, baby);
        return toResponse(repository.save(record));
    }

    @Transactional
    public WeightResponse update(Long id, WeightRequest request) {
        WeightRecord record = require(id);
        apply(record, request, record.getBaby());
        return toResponse(repository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        WeightRecord record = require(id);
        record.setDeletedAt(timeService.now());
        repository.save(record);
    }

    @Transactional(readOnly = true)
    public List<WeightRecord> recordsBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return repository.findAllByBabyIdAndDeletedAtIsNullAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtDesc(
                babyId, from, to);
    }

    public WeightResponse toResponse(WeightRecord record) {
        return WeightResponse.builder()
                .id(record.getId())
                .clientRequestId(record.getClientRequestId())
                .measuredAt(record.getMeasuredAt())
                .recordDate(record.getRecordDate())
                .weightKg(record.getWeightKg())
                .note(record.getNote())
                .build();
    }

    private void apply(WeightRecord record, WeightRequest request, BabyProfile baby) {
        validateWeight(request.getWeightKg());
        validateTime(request.getMeasuredAt(), baby);
        LocalDate recordDate = request.getMeasuredAt()
                .atZoneSameInstant(timeService.zone(baby.getTimezone()))
                .toLocalDate();
        WeightRecord existing = repository.findByBabyIdAndRecordDateAndDeletedAtIsNull(
                baby.getId(), recordDate).orElse(null);
        if (existing != null && !existing.getId().equals(record.getId())) {
            throw BusinessException.conflict(recordDate + " 已有体重记录，请编辑当天记录");
        }
        record.setMeasuredAt(request.getMeasuredAt());
        record.setRecordDate(recordDate);
        record.setWeightKg(request.getWeightKg());
        record.setNote(normalize(request.getNote()));
    }

    private void validateWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(MIN_WEIGHT_KG) < 0 || weight.compareTo(MAX_WEIGHT_KG) > 0) {
            throw BusinessException.validation("体重必须在 0.100 至 100.000 kg 之间");
        }
        if (weight.stripTrailingZeros().scale() > 3) {
            throw BusinessException.validation("体重最多保留 3 位小数");
        }
    }

    private void validateTime(OffsetDateTime measuredAt, BabyProfile baby) {
        if (measuredAt == null) {
            throw BusinessException.validation("测量时间不能为空");
        }
        if (measuredAt.isBefore(baby.getBirthTime())) {
            throw BusinessException.validation("测量时间不能早于宝宝出生时间");
        }
        if (measuredAt.isAfter(timeService.now().plusMinutes(5))) {
            throw BusinessException.validation("测量时间不能晚于当前时间");
        }
    }

    private void validateRangeAndPage(LocalDate from, LocalDate to, int page, int size) {
        if (page < 0 || size < 1) {
            throw BusinessException.validation("分页参数必须为正数");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw BusinessException.validation("开始日期必须早于结束日期");
        }
    }

    private WeightRecord require(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> BusinessException.notFound("体重记录不存在或已删除"));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

