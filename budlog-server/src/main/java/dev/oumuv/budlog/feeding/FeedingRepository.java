package dev.oumuv.budlog.feeding;

import dev.oumuv.budlog.common.RecordMapper;
import dev.oumuv.budlog.common.RecordPage;
import dev.oumuv.budlog.common.PageSpec;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface FeedingRepository extends RecordMapper<FeedingRecord> {

    default Optional<FeedingRecord> findFirstByBabyIdAndDeletedAtIsNullOrderByStartTimeDesc(Long babyId) {
        return Optional.ofNullable(selectOne(live(babyId).orderByDesc("start_time").last("LIMIT 1")));
    }

    default Optional<FeedingRecord> findFirstByBabyIdAndDeletedAtIsNullAndFeedingTypeInOrderByStartTimeDesc(Long babyId, List<FeedingType> feedingTypes) {
        return Optional.ofNullable(selectOne(live(babyId).in("feeding_type", feedingTypes).orderByDesc("start_time").last("LIMIT 1")));
    }

    default Optional<FeedingRecord> findFirstByBabyIdAndDeletedAtIsNullAndStartTimeLessThanOrderByStartTimeDesc(Long babyId, OffsetDateTime startTime) {
        return Optional.ofNullable(selectOne(live(babyId).lt("start_time", startTime).orderByDesc("start_time").last("LIMIT 1")));
    }

    default Optional<FeedingRecord> findFirstByBabyIdAndDeletedAtIsNullAndStartTimeLessThanOrderByStartTimeDescIdDesc(Long babyId, OffsetDateTime startTime) {
        return Optional.ofNullable(selectOne(live(babyId).lt("start_time", startTime).orderByDesc("start_time").orderByDesc("id").last("LIMIT 1")));
    }

    default RecordPage<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullOrderByStartTimeDesc(Long babyId, PageSpec pageable) {
        return page(live(babyId).orderByDesc("start_time"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualOrderByStartTimeDesc(Long babyId, OffsetDateTime from, PageSpec pageable) {
        return page(live(babyId).ge("start_time", from).orderByDesc("start_time"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullAndStartTimeLessThanOrderByStartTimeDesc(Long babyId, OffsetDateTime to, PageSpec pageable) {
        return page(live(babyId).lt("start_time", to).orderByDesc("start_time"), pageable.getPage(), pageable.getSize());
    }

    default RecordPage<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDesc(Long babyId, OffsetDateTime from, OffsetDateTime to, PageSpec pageable) {
        return page(live(babyId).ge("start_time", from).lt("start_time", to).orderByDesc("start_time"), pageable.getPage(), pageable.getSize());
    }

    default List<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDesc(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return selectList(live(babyId).ge("start_time", from).lt("start_time", to).orderByDesc("start_time"));
    }

    default RecordPage<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDescIdDesc(Long babyId, OffsetDateTime from, OffsetDateTime to, PageSpec pageable) {
        return page(live(babyId).ge("start_time", from).lt("start_time", to).orderByDesc("start_time").orderByDesc("id"), pageable.getPage(), pageable.getSize());
    }
}
