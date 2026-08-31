package dev.oumuv.budlog.event;

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
import java.util.List;

@Service
public class EventService {

    private final EventRepository repository;
    private final BabyService babyService;
    private final TimeService timeService;

    public EventService(EventRepository repository, BabyService babyService, TimeService timeService) {
        this.repository = repository;
        this.babyService = babyService;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> list(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        BabyProfile baby = babyService.requireCurrent();
        validateRangeAndPage(from, to, page, size);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<EventRecord> records;
        if (from != null && to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                    baby.getId(), from, to, pageable);
        } else if (from != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                    baby.getId(), from, pageable);
        } else if (to != null) {
            records = repository.findAllByBabyIdAndDeletedAtIsNullAndOccurredAtLessThanOrderByOccurredAtDesc(
                    baby.getId(), to, pageable);
        } else {
            records = repository.findAllByBabyIdAndDeletedAtIsNullOrderByOccurredAtDesc(baby.getId(), pageable);
        }
        List<EventResponse> content = new ArrayList<EventResponse>();
        for (EventRecord record : records.getContent()) {
            content.add(toResponse(record));
        }
        return PageResponse.from(records, content);
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public EventResponse create(EventRequest request) {
        EventRecord duplicate = repository.findByClientRequestId(request.getClientRequestId()).orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate);
        }
        BabyProfile baby = babyService.requireCurrent();
        EventRecord record = new EventRecord();
        record.setBaby(baby);
        record.setClientRequestId(request.getClientRequestId());
        apply(record, request, baby);
        return toResponse(repository.save(record));
    }

    @Transactional
    public EventResponse update(Long id, EventRequest request) {
        EventRecord record = require(id);
        apply(record, request, record.getBaby());
        return toResponse(repository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        EventRecord record = require(id);
        record.setDeletedAt(timeService.now());
        repository.save(record);
    }

    @Transactional(readOnly = true)
    public List<EventRecord> recordsBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return repository.findAllByBabyIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                babyId, from, to);
    }

    public EventResponse toResponse(EventRecord record) {
        return EventResponse.builder()
                .id(record.getId())
                .clientRequestId(record.getClientRequestId())
                .eventType(record.getEventType())
                .title(record.getTitle())
                .occurredAt(record.getOccurredAt())
                .note(record.getNote())
                .build();
    }

    private void apply(EventRecord record, EventRequest request, BabyProfile baby) {
        validateTime(request.getOccurredAt(), baby);
        record.setEventType(request.getEventType());
        record.setTitle(request.getTitle().trim());
        record.setOccurredAt(request.getOccurredAt());
        record.setNote(normalize(request.getNote()));
    }

    private void validateTime(OffsetDateTime occurredAt, BabyProfile baby) {
        if (occurredAt == null) {
            throw BusinessException.validation("发生时间不能为空");
        }
        if (occurredAt.isBefore(baby.getBirthTime())) {
            throw BusinessException.validation("发生时间不能早于宝宝出生时间");
        }
        if (occurredAt.isAfter(timeService.now().plusMinutes(5))) {
            throw BusinessException.validation("发生时间不能晚于当前时间");
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

    private EventRecord require(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> BusinessException.notFound("事件记录不存在或已删除"));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

