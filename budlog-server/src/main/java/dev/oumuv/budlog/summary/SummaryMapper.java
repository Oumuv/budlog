package dev.oumuv.budlog.summary;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface SummaryMapper {

    // Independent aggregates avoid multiplying counts/amounts through joins.
    // Truncate each direct-feeding duration before summing, matching Duration.toMinutes().
    @Select({
            "SELECT f.feeding_count, f.bottle_amount_ml, f.direct_feeding_minutes,",
            "       m.milk_storage_count, m.stored_milk_amount_ml, d.pee_count, d.poop_count",
            "FROM (SELECT COUNT(*) AS feeding_count,",
            " COALESCE(SUM(CASE WHEN feeding_type <> 'BREAST_DIRECT' THEN amount_ml ELSE 0 END), 0) AS bottle_amount_ml,",
            " COALESCE(SUM(CASE WHEN feeding_type = 'BREAST_DIRECT' AND end_time IS NOT NULL",
            "   THEN GREATEST(0, TRUNC(EXTRACT(EPOCH FROM (end_time - start_time)) / 60))",
            "   ELSE 0 END), 0) AS direct_feeding_minutes",
            " FROM feeding_record WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            " AND start_time >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            " AND start_time < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}) f",
            "CROSS JOIN (SELECT COUNT(*) FILTER (WHERE record_type IN ('PEE', 'BOTH')) AS pee_count,",
            " COUNT(*) FILTER (WHERE record_type IN ('POOP', 'BOTH')) AS poop_count",
            " FROM diaper_record WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            " AND record_time >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            " AND record_time < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}) d",
            "CROSS JOIN (SELECT COUNT(*) AS milk_storage_count, COALESCE(SUM(amount_ml), 0) AS stored_milk_amount_ml",
            " FROM milk_storage_record WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            " AND stored_at >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            " AND stored_at < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}) m"
    })
    @Results({
            @Result(column = "feeding_count", property = "feedingCount"),
            @Result(column = "bottle_amount_ml", property = "bottleAmountMl"),
            @Result(column = "direct_feeding_minutes", property = "directFeedingMinutes"),
            @Result(column = "milk_storage_count", property = "milkStorageCount"),
            @Result(column = "stored_milk_amount_ml", property = "storedMilkAmountMl"),
            @Result(column = "pee_count", property = "peeCount"),
            @Result(column = "poop_count", property = "poopCount")
    })
    SummaryTotals selectCore(@Param("babyId") Long babyId,
                             @Param("start") OffsetDateTime start,
                             @Param("end") OffsetDateTime end);

    @Select({
            "SELECT (SELECT weight_kg FROM weight_record",
            " WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            " AND measured_at >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            " AND measured_at < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            " ORDER BY measured_at DESC, id DESC LIMIT 1) AS weight_kg,",
            " (SELECT COUNT(*) FROM event_record WHERE baby_id = #{babyId} AND deleted_at IS NULL",
            " AND occurred_at >= #{start,jdbcType=TIMESTAMP_WITH_TIMEZONE}",
            " AND occurred_at < #{end,jdbcType=TIMESTAMP_WITH_TIMEZONE}) AS event_count"
    })
    @Results({
            @Result(column = "weight_kg", property = "weightKg"),
            @Result(column = "event_count", property = "eventCount")
    })
    SummaryTotals selectExtras(@Param("babyId") Long babyId,
                               @Param("start") OffsetDateTime start,
                               @Param("end") OffsetDateTime end);
}
