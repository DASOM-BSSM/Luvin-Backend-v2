package com.luvin.simulation.repository;

import com.luvin.simulation.domain.EpisodeParticipant;
import com.luvin.simulation.domain.ParticipantType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EpisodeParticipantRepository extends JpaRepository<EpisodeParticipant, Long> {
    List<EpisodeParticipant> findAllByEpisode_EpisodeId(Long episodeId);
    Optional<EpisodeParticipant> findByParticipantIdAndEpisode_EpisodeId(Long participantId, Long episodeId);
    Optional<EpisodeParticipant> findFirstByEpisode_EpisodeIdAndParticipantType(Long episodeId, ParticipantType participantType);
}
