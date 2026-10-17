package com.luvin.simulation.repository;

import com.luvin.simulation.domain.VoteReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoteReasonRepository extends JpaRepository<VoteReason, Long> {
    Optional<VoteReason> findByEpisode_EpisodeId(Long episodeId);
}
