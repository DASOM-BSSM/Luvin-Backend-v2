package com.luvin.simulation.repository;

import com.luvin.simulation.domain.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findAllByEpisode_EpisodeId(Long episodeId);
    Optional<Match> findFirstByEpisode_MemberIdAndIsFinalTrue(Long memberId);
}
