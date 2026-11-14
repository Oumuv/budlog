package dev.oumuv.budlog.event;

import dev.oumuv.budlog.common.RecordMapper;
import dev.oumuv.budlog.common.RecordPage;
import dev.oumuv.budlog.common.PageSpec;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface EventRepository extends RecordMapper<EventRecord> {

    default RecordPage<EventRecord> findAllByBabyIdAndDeletedAtIsNullOrderByOccurredAtDesc(Long babyId, PageSpec pageable) {
        return page(live(babyId).orderByDesc("occurred_at"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<EventRecord> findAllByBabyIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(Long babyId, OffsetDateTime from, PageSpec pageable) {
        return page(live(babyId).ge("occurred_at", from).orderByDesc("occurred_at"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<EventRecord> findAllByBabyIdAndDeletedAtIsNullAndOccurredAtLessThanOrderByOccurredAtDesc(Long babyId, OffsetDateTime to, PageSpec pageable) {
        return page(live(babyId).lt("occurred_at", to).orderByDesc("occurred_at"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<EventRecord> findAllByBabyIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(Long babyId, OffsetDateTime from, OffsetDateTime to, PageSpec pageable) {
        return page(live(babyId).ge("occurred_at", from).lt("occurred_at", to).orderByDesc("occurred_at"), pageable.getPage(), pageable.getSize());
    }

    default List<EventRecord> findAllByBabyIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return selectList(live(babyId).ge("occurred_at", from).lt("occurred_at", to).orderByDesc("occurred_at"));
    }
}
