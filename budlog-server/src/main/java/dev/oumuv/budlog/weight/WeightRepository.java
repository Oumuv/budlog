package dev.oumuv.budlog.weight;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeightRepository extends JpaRepository<WeightRecord, Long> {

    Optional<WeightRecord> findByIdAndDeletedAtIsNull(Long id);

    Optional<WeightRecord> findByClientRequestId(UUID clientRequestId);

    Optional<WeightRecord> findByBabyIdAndRecordDateAndDeletedAtIsNull(Long babyId, LocalDate recordDate);

    Page<WeightRecord> findAllByBabyIdAndDeletedAtIsNullOrderByRecordDateDescMeasuredAtDesc(
            Long babyId, Pageable pageable);

    Page<WeightRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordDateGreaterThanEqualOrderByRecordDateDescMeasuredAtDesc(
            Long babyId, LocalDate from, Pageable pageable);

    Page<WeightRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordDateLessThanOrderByRecordDateDescMeasuredAtDesc(
            Long babyId, LocalDate to, Pageable pageable);

    Page<WeightRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordDateGreaterThanEqualAndRecordDateLessThanOrderByRecordDateDescMeasuredAtDesc(
            Long babyId, LocalDate from, LocalDate to, Pageable pageable);

    List<WeightRecord> findAllByBabyIdAndDeletedAtIsNullAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtDesc(
            Long babyId, OffsetDateTime from, OffsetDateTime to);
}

