package com.luvin.ai.repository;

import com.luvin.ai.domain.AiEpisodeUnlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiEpisodeUnlockRepository extends JpaRepository<AiEpisodeUnlock, Long> {
    boolean existsBySeason_IdAndEpisodeOrder(Long seasonPk, Integer episodeOrder);

    List<AiEpisodeUnlock> findAllBySeason_Id(Long seasonPk);

    void deleteAllBySeason_Id(Long seasonPk);
}
