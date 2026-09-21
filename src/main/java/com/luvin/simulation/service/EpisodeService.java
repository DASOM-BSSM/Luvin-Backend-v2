package com.luvin.simulation.service;

import com.luvin.simulation.dto.EpisodeCreateRequest;
import com.luvin.simulation.dto.EpisodeResponse;
import com.luvin.simulation.dto.ParticipantResponse;

import java.util.List;

public interface EpisodeService {
    List<EpisodeResponse> getEpisodes(Long memberId);
    EpisodeResponse getEpisode(Long memberId, Long episodeId);
    EpisodeResponse createEpisode(Long memberId, EpisodeCreateRequest request);
    List<ParticipantResponse> getParticipants(Long memberId, Long episodeId);
}
