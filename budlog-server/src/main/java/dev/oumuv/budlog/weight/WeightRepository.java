package dev.oumuv.budlog.weight;

import dev.oumuv.budlog.common.RecordMapper;
import dev.oumuv.budlog.common.RecordPage;
import dev.oumuv.budlog.common.PageSpec;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface WeightRepository extends RecordMapper<WeightRecord> {

    default Optional<WeightRecord> findByBabyIdAndRecordDateAndDeletedAtIsNull(Long babyId, LocalDate recordDate) {
        return Optional.ofNullable(selectOne(live(babyId).eq("record_date", recordDate)));
    }

    default Optional<WeightRecord> findFirstByBabyIdAndDeletedAtIsNullAndMeasuredAtLessThanOrderByMeasuredAtDescIdDesc(Long babyId, OffsetDateTime before) {
        return Optional.ofNullable(selectOne(live(babyId).lt("measured_at", before).orderByDesc("measured_at").orderByDesc("id").last("LIMIT 1")));
    }

    default RecordPage<WeightRecord> findAllByBabyIdAndDeletedAtIsNullOrderByRecordDateDescMeasuredAtDesc(Long babyId, PageSpec pageable) {
        return page(live(babyId).orderByDesc("record_date", "measured_at"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<WeightRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordDateGreaterThanEqualOrderByRecordDateDescMeasuredAtDesc(Long babyId, LocalDate from, PageSpec pageable) {
        return page(live(babyId).ge("record_date", from).orderByDesc("record_date", "measured_at"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<WeightRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordDateLessThanOrderByRecordDateDescMeasuredAtDesc(Long babyId, LocalDate to, PageSpec pageable) {
        return page(live(babyId).lt("record_date", to).orderByDesc("record_date", "measured_at"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<WeightRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordDateGreaterThanEqualAndRecordDateLessThanOrderByRecordDateDescMeasuredAtDesc(Long babyId, LocalDate from, LocalDate to, PageSpec pageable) {
        return page(live(babyId).ge("record_date", from).lt("record_date", to).orderByDesc("record_date", "measured_at"), pageable.getPage(), pageable.getSize());
    }

    default List<WeightRecord> findAllByBabyIdAndDeletedAtIsNullAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtDesc(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return selectList(live(babyId).ge("measured_at", from).lt("measured_at", to).orderByDesc("measured_at"));
    }
}
