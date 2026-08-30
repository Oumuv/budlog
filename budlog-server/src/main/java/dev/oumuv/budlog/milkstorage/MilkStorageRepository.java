package dev.oumuv.budlog.milkstorage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilkStorageRepository extends JpaRepository<MilkStorageRecord, Long> {

    Optional<MilkStorageRecord> findByIdAndDeletedAtIsNull(Long id);

    Optional<MilkStorageRecord> findByClientRequestId(UUID clientRequestId);

    Page<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullOrderByStoredAtDesc(Long babyId, Pageable pageable);

    Page<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualOrderByStoredAtDesc(
            Long babyId, OffsetDateTime from, Pageable pageable);

    Page<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullAndStoredAtLessThanOrderByStoredAtDesc(
            Long babyId, OffsetDateTime to, Pageable pageable);

    Page<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualAndStoredAtLessThanOrderByStoredAtDesc(
            Long babyId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    List<MilkStorageRecord> findAllByBabyIdAndDeletedAtIsNullAndStoredAtGreaterThanEqualAndStoredAtLessThanOrderByStoredAtDesc(
            Long babyId, OffsetDateTime from, OffsetDateTime to);
}
