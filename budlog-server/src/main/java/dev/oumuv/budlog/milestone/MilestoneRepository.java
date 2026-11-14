package dev.oumuv.budlog.milestone;

import dev.oumuv.budlog.common.RecordMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface MilestoneRepository extends RecordMapper<CustomMilestone> {
    default List<CustomMilestone> findAllByBabyIdAndDeletedAtIsNullOrderByTargetTimeAsc(Long babyId) {
        return selectList(live(babyId).orderByAsc("target_time"));
    }

    default List<CustomMilestone> findBetween(Long babyId, OffsetDateTime from, OffsetDateTime to) {
        return selectList(between(babyId, "target_time", from, to).orderByAsc("target_time"));
    }
}
