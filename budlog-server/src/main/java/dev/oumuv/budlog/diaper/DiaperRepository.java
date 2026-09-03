package dev.oumuv.budlog.diaper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiaperRepository extends JpaRepository<DiaperRecord, Long> {

    Optional<DiaperRecord> findByIdAndDeletedAtIsNull(Long id);

    Optional<DiaperRecord> findByClientRequestId(UUID clientRequestId);

    Optional<DiaperRecord> findFirstByBabyIdAndDeletedAtIsNullAndRecordTypeInOrderByRecordTimeDesc(
            Long babyId, List<DiaperType> types);

    Optional<DiaperRecord> findFirstByBabyIdAndDeletedAtIsNullAndRecordTimeLessThanOrderByRecordTimeDesc(
            Long babyId, OffsetDateTime recordTime);

    Page<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullOrderByRecordTimeDesc(Long babyId, Pageable pageable);

    Page<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualAndRecordTimeLessThanOrderByRecordTimeDesc(
            Long babyId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualOrderByRecordTimeDesc(
            Long babyId, OffsetDateTime from, Pageable pageable);

    Page<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordTimeLessThanOrderByRecordTimeDesc(
            Long babyId, OffsetDateTime to, Pageable pageable);

    List<DiaperRecord> findAllByBabyIdAndDeletedAtIsNullAndRecordTimeGreaterThanEqualAndRecordTimeLessThanOrderByRecordTimeDesc(
            Long babyId, OffsetDateTime from, OffsetDateTime to);
}
