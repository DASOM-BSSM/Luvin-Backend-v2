package com.luvin.simulation.repository;

import com.luvin.simulation.domain.RebakeMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RebakeMessageRepository extends JpaRepository<RebakeMessage, Long> {
    List<RebakeMessage> findAllByEpisode_EpisodeIdAndAlternativeParticipant_ParticipantIdOrderBySequenceAsc(
            Long episodeId, Long alternativeParticipantId);
}
