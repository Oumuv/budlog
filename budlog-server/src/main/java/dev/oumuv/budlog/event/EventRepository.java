package dev.oumuv.budlog.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<EventRecord, Long> {

    Optional<EventRecord> findByIdAndDeletedAtIsNull(Long id);

    Optional<EventRecord> findByClientRequestId(UUID clientRequestId);

    Page<EventRecord> findAllByBabyIdAndDeletedAtIsNullOrderByOccurredAtDesc(Long babyId, Pageable pageable);

    Page<EventRecord> findAllByBabyIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            Long babyId, OffsetDateTime from, Pageable pageable);

    Page<EventRecord> findAllByBabyIdAndDeletedAtIsNullAndOccurredAtLessThanOrderByOccurredAtDesc(
            Long babyId, OffsetDateTime to, Pageable pageable);

    Page<EventRecord> findAllByBabyIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            Long babyId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    List<EventRecord> findAllByBabyIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            Long babyId, OffsetDateTime from, OffsetDateTime to);
}

