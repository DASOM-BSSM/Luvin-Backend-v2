package com.luvin.ai.repository;

import com.luvin.ai.domain.AiEpisodeMessageLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiEpisodeMessageLikeRepository extends JpaRepository<AiEpisodeMessageLike, Long> {
    Optional<AiEpisodeMessageLike> findBySeason_IdAndEpisodeNumberAndMessageId(
            Long seasonPk, Integer episodeNumber, UUID messageId);

    List<AiEpisodeMessageLike> findBySeason_IdAndEpisodeNumber(Long seasonPk, Integer episodeNumber);
}
