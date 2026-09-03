package com.luvin.simulation.repository;

import com.luvin.simulation.domain.AiClone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiCloneRepository extends JpaRepository<AiClone, Long> {
    Optional<AiClone> findTopByUserIdOrderByIdDesc(Long userId);
    Optional<AiClone> findByIdAndUserId(Long id, Long userId);
}
