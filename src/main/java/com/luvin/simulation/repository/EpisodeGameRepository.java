package com.luvin.simulation.repository;

import com.luvin.simulation.domain.EpisodeGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EpisodeGameRepository extends JpaRepository<EpisodeGame, Long> {
    List<EpisodeGame> findAllByOrderByGameOrderAsc();
}
