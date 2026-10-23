package com.luvin.ai.repository;

import com.luvin.ai.domain.AiStoryFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiStoryFeedbackRepository extends JpaRepository<AiStoryFeedback, Long> {
    Optional<AiStoryFeedback> findBySeason_IdAndMessageId(Long seasonPk, UUID messageId);

    List<AiStoryFeedback> findAllBySeason_IdAndEpisodeOrder(Long seasonPk, Integer episodeOrder);

    List<AiStoryFeedback> findTop10BySeason_IdOrderByUpdatedAtDesc(Long seasonPk);

    void deleteAllBySeason_Id(Long seasonPk);
}
