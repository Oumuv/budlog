package dev.oumuv.budlog.milkstorage;

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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MilkStorageService {

    private static final BigDecimal MAX_AMOUNT_ML = new BigDecimal("1000");

    private final MilkStorageRepository repository;
    private final BabyService babyService;
    private final TimeService timeService;

    public MilkStorageService(
            MilkStorageRepository repository,
            BabyService babyService,
            TimeService timeService) {
        this.repository = repository;
        this.babyService = babyService;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<MilkStorageResponse> list(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        BabyProfile baby = babyService.requireCurrent();
        validateRangeAndPage(from, to, page, size);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<MilkStorageRecord> records;
        if (from != null && to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualAndStoredAtLessThanOrderByStoredAtDesc(
                    baby.getId(), from, to, pageable);
        } else if (from != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualOrderByStoredAtDesc(
                    baby.getId(), from, pageable);
        } else if (to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndStoredAtLessThanOrderByStoredAtDesc(
                    baby.getId(), to, pageable);
        } else {
            records = repository.findAllByBabyIdAndDeletedAtIsNullOrderByStoredAtDesc(baby.getId(), pageable);
        }
        List<MilkStorageResponse> content = new ArrayList<MilkStorageResponse>();
        for (MilkStorageRecord record : records.getContent()) {
            content.add(toResponse(record));
        }
        return PageResponse.from(records, content);
    }

    @Transactional(readOnly = true)
    public MilkStorageResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public MilkStorageResponse create(MilkStorageRequest request) {
        MilkStorageRecord duplicate = repository.findByClientRequestId(request.getClientRequestId()).orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate);
        }
        MilkStorageRecord record = new MilkStorageRecord();
        record.setBaby(babyService.requireCurrent());
        record.setClientRequestId(request.getClientRequestId());
        apply(record, request);
        return toResponse(repository.save(record));
    }

    @Transactional
    public MilkStorageResponse update(Long id, MilkStorageRequest request) {
        MilkStorageRecord record = require(id);
        apply(record, request);
        return toResponse(repository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        MilkStorageRecord record = require(id);
        record.setDeletedAt(timeService.now());
        repository.save(record);
    }

    @Transactional(readOnly = true)
    public List<MilkStorageRecord> recordsBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return repository.findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualAndStoredAtLessThanOrderByStoredAtDesc(
                babyId, from, to);
    }

    public MilkStorageResponse toResponse(MilkStorageRecord record) {
        return MilkStorageResponse.builder()
                .id(record.getId())
                .clientRequestId(record.getClientRequestId())
                .storedAt(record.getStoredAt())
                .amountMl(record.getAmountMl())
                .note(record.getNote())
                .build();
    }

    private void apply(MilkStorageRecord record, MilkStorageRequest request) {
        validateAmount(request.getAmountMl());
        record.setStoredAt(request.getStoredAt());
        record.setAmountMl(request.getAmountMl());
        record.setNote(normalize(request.getNote()));
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(MAX_AMOUNT_ML) > 0) {
            throw BusinessException.validation("存奶量必须大于 0 且不能超过 1000 ml");
        }
        if (amount.stripTrailingZeros().scale() > 1) {
            throw BusinessException.validation("存奶量最多保留 1 位小数");
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

    private MilkStorageRecord require(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> BusinessException.notFound("存奶记录不存在或已删除"));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
