package dev.oumuv.budlog.feeding;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedingRepository extends JpaRepository<FeedingRecord, Long> {

    Optional<FeedingRecord> findByIdAndDeletedAtIsNull(Long id);

    Optional<FeedingRecord> findByClientRequestId(UUID clientRequestId);

    Optional<FeedingRecord> findFirstByBabyIdAndDeletedAtIsNullOrderByStartTimeDesc(Long babyId);

    Optional<FeedingRecord> findFirstByBabyIdAndDeletedAtIsNullAndStartTimeLessThanOrderByStartTimeDesc(
            Long babyId, OffsetDateTime startTime);

    Page<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullOrderByStartTimeDesc(Long babyId, Pageable pageable);

    Page<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualOrderByStartTimeDesc(
            Long babyId, OffsetDateTime from, Pageable pageable);

    Page<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullAndStartTimeLessThanOrderByStartTimeDesc(
            Long babyId, OffsetDateTime to, Pageable pageable);

    Page<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDesc(
            Long babyId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    List<FeedingRecord> findAllByBabyIdAndDeletedAtIsNullAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeDesc(
            Long babyId, OffsetDateTime from, OffsetDateTime to);
}

