package dev.oumuv.budlog.baby;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BabyRepository extends JpaRepository<BabyProfile, Long> {

    Optional<BabyProfile> findFirstByOrderByIdAsc();
}

