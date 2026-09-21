package com.luvin.simulation.repository;

import com.luvin.simulation.domain.EpisodeGameResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeGameResultRepository extends JpaRepository<EpisodeGameResult, Long> {
}
