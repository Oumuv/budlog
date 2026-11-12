package dev.oumuv.budlog.diaper;

import dev.oumuv.budlog.common.RecordMapper;
import dev.oumuv.budlog.common.RecordPage;
import dev.oumuv.budlog.common.PageSpec;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface DiaperRepository extends RecordMapper<DiaperRecord> {

    default Optional<DiaperRecord> findFirstByBabyIdAndDeletedAtIsNullAndRecordTypeInOrderByRecordTimeDesc(Long babyId, List<DiaperType> types) {
        return Optional.ofNullable(selectOne(live(babyId).in("record_type", types).orderByDesc("record_time").last("LIMIT 1")));
    }

    default Optional<DiaperRecord> findFirstByBabyIdAndDeletedAtIsNullAndRecordTimeLessThanOrderByRecordTimeDesc(Long babyId, OffsetDateTime recordTime) {
        return Optional.ofNullable(selectOne(live(babyId).lt("record_time", recordTime).orderByDesc("record_time").last("LIMIT 1")));
    }

    default RecordPage<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullOrderByRecordTimeDesc(Long babyId, PageSpec pageable) {
        return page(live(babyId).orderByDesc("record_time"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualAndRecordTimeLessThanOrderByRecordTimeDesc(Long babyId, OffsetDateTime from, OffsetDateTime to, PageSpec pageable) {
        return page(live(babyId).ge("record_time", from).lt("record_time", to).orderByDesc("record_time"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualOrderByRecordTimeDesc(Long babyId, OffsetDateTime from, PageSpec pageable) {
        return page(live(babyId).ge("record_time", from).orderByDesc("record_time"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordTimeLessThanOrderByRecordTimeDesc(Long babyId, OffsetDateTime to, PageSpec pageable) {
        return page(live(babyId).lt("record_time", to).orderByDesc("record_time"), pageable.getPage(), pageable.getSize());
    }

    default List<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualAndRecordTimeLessThanOrderByRecordTimeDesc(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return selectList(live(babyId).ge("record_time", from).lt("record_time", to).orderByDesc("record_time"));
    }
}
