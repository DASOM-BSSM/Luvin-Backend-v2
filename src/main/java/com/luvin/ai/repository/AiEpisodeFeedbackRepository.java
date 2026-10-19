package com.luvin.ai.repository;

import com.luvin.ai.domain.AiEpisodeFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiEpisodeFeedbackRepository extends JpaRepository<AiEpisodeFeedback, Long> {
    Optional<AiEpisodeFeedback> findBySeason_IdAndEpisodeNumber(Long seasonPk, Integer episodeNumber);
}
