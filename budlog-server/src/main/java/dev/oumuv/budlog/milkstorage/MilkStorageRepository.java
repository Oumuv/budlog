package dev.oumuv.budlog.milkstorage;

import dev.oumuv.budlog.common.RecordMapper;
import dev.oumuv.budlog.common.RecordPage;
import dev.oumuv.budlog.common.PageSpec;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface MilkStorageRepository extends RecordMapper<MilkStorageRecord> {

    default RecordPage<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullOrderByStoredAtDesc(Long babyId, PageSpec pageable) {
        return page(live(babyId).orderByDesc("stored_at"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualOrderByStoredAtDesc(Long babyId, OffsetDateTime from, PageSpec pageable) {
        return page(live(babyId).ge("stored_at", from).orderByDesc("stored_at"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullAndStoredAtLessThanOrderByStoredAtDesc(Long babyId, OffsetDateTime to, PageSpec pageable) {
        return page(live(babyId).lt("stored_at", to).orderByDesc("stored_at"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualAndStoredAtLessThanOrderByStoredAtDesc(Long babyId, OffsetDateTime from, OffsetDateTime to, PageSpec pageable) {
        return page(live(babyId).ge("stored_at", from).lt("stored_at", to).orderByDesc("stored_at"), pageable.getPage(), pageable.getSize());
    }

    default List<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualAndStoredAtLessThanOrderByStoredAtDesc(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return selectList(live(babyId).ge("stored_at", from).lt("stored_at", to).orderByDesc("stored_at"));
    }
}
