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

    /** 새 시즌 설정의 likedLines: 지난 시즌에 하트를 누른 대사(누구의 대사든) 최근 순. */
    List<AiStoryMessageLike> findTop10BySeason_IdOrderByCreatedAtDesc(Long seasonPk);

    void deleteAllBySeason_Id(Long seasonPk);
}
