package com.luvin.simulation.repository;

import com.luvin.simulation.domain.GroupConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupConversationMessageRepository extends JpaRepository<GroupConversationMessage, Long> {
    List<GroupConversationMessage> findAllByEpisode_EpisodeIdOrderBySequenceAsc(Long episodeId);
}
