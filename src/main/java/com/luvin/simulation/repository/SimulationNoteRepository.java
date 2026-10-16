package com.luvin.simulation.repository;

import com.luvin.simulation.domain.SimulationNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulationNoteRepository extends JpaRepository<SimulationNote, Long> {
    Optional<SimulationNote> findFirstByEpisode_EpisodeIdOrderByCreatedAtDesc(Long episodeId);
}
