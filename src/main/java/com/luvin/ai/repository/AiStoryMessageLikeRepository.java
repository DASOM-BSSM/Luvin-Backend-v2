package com.luvin.ai.repository;

import com.luvin.ai.domain.AiStoryMessageLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiStoryMessageLikeRepository extends JpaRepository<AiStoryMessageLike, Long> {
    Optional<AiStoryMessageLike> findBySeason_IdAndMessageId(Long seasonPk, UUID messageId);

    List<AiStoryMessageLike> findAllBySeason_IdAndEpisodeOrder(Long seasonPk, Integer episodeOrder);

    List<AiStoryMessageLike> findTop10BySeason_IdAndFromMeTrueOrderByCreatedAtDesc(Long seasonPk);

    void deleteAllBySeason_Id(Long seasonPk);
}
