package com.luvin.ai.repository;

import com.luvin.ai.domain.AiSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiSelectionRepository extends JpaRepository<AiSelection, Long> {
    Optional<AiSelection> findBySeason_IdAndEpisodeNumber(Long seasonPk, Integer episodeNumber);

    Optional<AiSelection> findBySourceEventId(UUID sourceEventId);

    boolean existsBySeason_IdAndEpisodeNumber(Long seasonPk, Integer episodeNumber);
}
