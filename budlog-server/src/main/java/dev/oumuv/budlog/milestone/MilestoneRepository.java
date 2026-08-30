package dev.oumuv.budlog.milestone;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<CustomMilestone, Long> {

    List<CustomMilestone> findAllByBabyIdAndDeletedAtIsNullOrderByTargetTimeAsc(Long babyId);

    Optional<CustomMilestone> findByIdAndDeletedAtIsNull(Long id);

    Optional<CustomMilestone> findByClientRequestId(UUID clientRequestId);
}

