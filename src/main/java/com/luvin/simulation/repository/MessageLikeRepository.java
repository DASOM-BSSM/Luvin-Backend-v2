package com.luvin.simulation.repository;

import com.luvin.simulation.domain.MessageLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageLikeRepository extends JpaRepository<MessageLike, Long> {
    Optional<MessageLike> findByMemberIdAndEpisode_EpisodeId(Long memberId, Long episodeId);

    List<MessageLike> findAllByMemberIdOrderByEpisode_EpisodeNumberAsc(Long memberId);
}
