package dev.oumuv.budlog.diaper;

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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DiaperService {

    private final DiaperRepository repository;
    private final BabyService babyService;
    private final TimeService timeService;

    public DiaperService(DiaperRepository repository, BabyService babyService, TimeService timeService) {
        this.repository = repository;
        this.babyService = babyService;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<DiaperResponse> list(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        BabyProfile baby = babyService.requireCurrent();
        validateRangeAndPage(from, to, page, size);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<DiaperRecord> records;
        if (from != null && to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualAndRecordTimeLessThanOrderByRecordTimeDesc(
                    baby.getId(), from, to, pageable);
        } else if (from != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualOrderByRecordTimeDesc(
                    baby.getId(), from, pageable);
        } else if (to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndRecordTimeLessThanOrderByRecordTimeDesc(
                    baby.getId(), to, pageable);
        } else {
            records = repository.findAllByBabyIdAndDeletedAtIsNullOrderByRecordTimeDesc(baby.getId(), pageable);
        }
        List<DiaperResponse> content = new ArrayList<DiaperResponse>();
        for (DiaperRecord record : records.getContent()) {
            content.add(toResponse(record));
        }
        return PageResponse.from(records, content);
    }

    @Transactional(readOnly = true)
    public DiaperResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public DiaperResponse create(DiaperRequest request) {
        DiaperRecord duplicate = repository.findByClientRequestId(request.getClientRequestId()).orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate);
        }
        DiaperRecord record = new DiaperRecord();
        record.setBaby(babyService.requireCurrent());
        record.setClientRequestId(request.getClientRequestId());
        apply(record, request);
        return toResponse(repository.save(record));
    }

    @Transactional
    public DiaperResponse update(Long id, DiaperRequest request) {
        DiaperRecord record = require(id);
        apply(record, request);
        return toResponse(repository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        DiaperRecord record = require(id);
        record.setDeletedAt(timeService.now());
        repository.save(record);
    }

    @Transactional(readOnly = true)
    public List<DiaperRecord> recordsBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return repository.findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualAndRecordTimeLessThanOrderByRecordTimeDesc(
                babyId, from, to);
    }

    @Transactional(readOnly = true)
    public DiaperRecord latestBefore(Long babyId, OffsetDateTime recordTime) {
        return repository.findFirstByBabyIdAndDeletedAtIsNullAndRecordTimeLessThanOrderByRecordTimeDesc(
                babyId, recordTime).orElse(null);
    }

    @Transactional(readOnly = true)
    public DiaperRecord lastPee(Long babyId) {
        return repository.findFirstByBabyIdAndDeletedAtIsNullAndRecordTypeInOrderByRecordTimeDesc(
                babyId, Arrays.asList(DiaperType.PEE, DiaperType.BOTH)).orElse(null);
    }

    @Transactional(readOnly = true)
    public DiaperRecord lastPoop(Long babyId) {
        return repository.findFirstByBabyIdAndDeletedAtIsNullAndRecordTypeInOrderByRecordTimeDesc(
                babyId, Arrays.asList(DiaperType.POOP, DiaperType.BOTH)).orElse(null);
    }

    public DiaperResponse toResponse(DiaperRecord record) {
        if (record == null) {
            return null;
        }
        return DiaperResponse.builder()
                .id(record.getId())
                .clientRequestId(record.getClientRequestId())
                .recordType(record.getRecordType())
                .recordTime(record.getRecordTime())
                .note(record.getNote())
                .build();
    }

    private void apply(DiaperRecord record, DiaperRequest request) {
        record.setRecordType(request.getRecordType());
        record.setRecordTime(request.getRecordTime());
        record.setNote(normalize(request.getNote()));
    }

    private DiaperRecord require(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> BusinessException.notFound("尿便记录不存在或已删除"));
    }

    private void validateRangeAndPage(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        if (page < 0 || size < 1) {
            throw BusinessException.validation("分页参数必须为正数");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw BusinessException.validation("开始时间必须早于结束时间");
        }
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
