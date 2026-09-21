package com.luvin.simulation.repository;

import com.luvin.simulation.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    List<Episode> findAllByMemberIdOrderByEpisodeNumberAsc(Long memberId);
    Optional<Episode> findByEpisodeIdAndMemberId(Long episodeId, Long memberId);
}
