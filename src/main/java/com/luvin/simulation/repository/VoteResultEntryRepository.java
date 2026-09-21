package com.luvin.simulation.repository;

import com.luvin.simulation.domain.VoteResultEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoteResultEntryRepository extends JpaRepository<VoteResultEntry, Long> {
    List<VoteResultEntry> findAllByEpisode_EpisodeId(Long episodeId);
}
