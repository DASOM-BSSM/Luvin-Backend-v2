package com.luvin.simulation.repository;

import com.luvin.simulation.domain.EpisodeVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeVoteRepository extends JpaRepository<EpisodeVote, Long> {
    boolean existsByEpisode_EpisodeId(Long episodeId);
}
