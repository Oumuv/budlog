package dev.oumuv.budlog.timeline;

import dev.oumuv.budlog.diaper.DiaperRecord;
import dev.oumuv.budlog.event.EventRecord;
import dev.oumuv.budlog.feeding.FeedingRecord;
import dev.oumuv.budlog.milkstorage.MilkStorageRecord;
import dev.oumuv.budlog.task.TodoTask;
import dev.oumuv.budlog.weight.WeightRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/** Bounded, per-category candidates; only the fields used by timeline formatting are loaded. */
@Mapper
public interface RecentTimelineMapper {

    // id DESC makes equal-time selection deterministic (the original DAOs left it unspecified).
    // Each query uses the same local-day [start, end) range and excludes soft-deleted records.
    @Select({
            "SELECT id, feeding_type, start_time, end_time, amount_ml FROM feeding_record",
            "WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            "AND start_time >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "AND start_time < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "ORDER BY start_time DESC, id DESC LIMIT #{limit}"
    })
    List<FeedingRecord> selectFeedings(@Param("babyId") Long babyId,
                                      @Param("start") OffsetDateTime start,
                                      @Param("end") OffsetDateTime end,
                                      @Param("limit") int limit);

    @Select({
            "SELECT id, record_type, record_time, note FROM diaper_record",
            "WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            "AND record_time >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "AND record_time < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "ORDER BY record_time DESC, id DESC LIMIT #{limit}"
    })
    List<DiaperRecord> selectDiapers(@Param("babyId") Long babyId,
                                    @Param("start") OffsetDateTime start,
                                    @Param("end") OffsetDateTime end,
                                    @Param("limit") int limit);

    @Select({
            "SELECT id, stored_at, amount_ml FROM milk_storage_record",
            "WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            "AND stored_at >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "AND stored_at < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "ORDER BY stored_at DESC, id DESC LIMIT #{limit}"
    })
    List<MilkStorageRecord> selectMilkStorages(@Param("babyId") Long babyId,
                                              @Param("start") OffsetDateTime start,
                                              @Param("end") OffsetDateTime end,
                                              @Param("limit") int limit);

    @Select({
            "SELECT id, completed_at, title FROM todo_task",
            "WHERE baby_id = #{babyId} AND deleted_at IS NULL AND status = 'DONE'",
            "AND completed_at >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "AND completed_at < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "ORDER BY completed_at DESC, id DESC LIMIT #{limit}"
    })
    List<TodoTask> selectTasks(@Param("babyId") Long babyId,
                               @Param("start") OffsetDateTime start,
                               @Param("end") OffsetDateTime end,
                               @Param("limit") int limit);

    @Select({
            "SELECT id, measured_at, weight_kg, note FROM weight_record",
            "WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            "AND measured_at >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "AND measured_at < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "ORDER BY measured_at DESC, id DESC LIMIT #{limit}"
    })
    List<WeightRecord> selectWeights(@Param("babyId") Long babyId,
                                     @Param("start") OffsetDateTime start,
                                     @Param("end") OffsetDateTime end,
                                     @Param("limit") int limit);

    @Select({
            "SELECT id, event_type, occurred_at, title, note FROM event_record",
            "WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            "AND occurred_at >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "AND occurred_at < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            "ORDER BY occurred_at DESC, id DESC LIMIT #{limit}"
    })
    List<EventRecord> selectEvents(@Param("babyId") Long babyId,
                                   @Param("start") OffsetDateTime start,
                                   @Param("end") OffsetDateTime end,
                                   @Param("limit") int limit);
}
