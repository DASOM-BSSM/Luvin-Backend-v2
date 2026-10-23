package com.luvin.ai.repository;

import com.luvin.ai.domain.AiEpisodeContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiEpisodeContentRepository extends JpaRepository<AiEpisodeContent, Long> {
    Optional<AiEpisodeContent> findBySeason_IdAndEpisodeOrder(Long seasonPk, Integer episodeOrder);

    List<AiEpisodeContent> findAllBySeason_IdOrderByEpisodeOrderAsc(Long seasonPk);

    void deleteAllBySeason_Id(Long seasonPk);
}
