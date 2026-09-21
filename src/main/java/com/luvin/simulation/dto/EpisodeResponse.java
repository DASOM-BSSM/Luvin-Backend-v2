package com.luvin.simulation.dto;

import com.luvin.simulation.domain.Episode;

public class EpisodeResponse {
    private final Long episodeId;
    private final Integer episodeNumber;
    private final String title;
    private final String status;

    public EpisodeResponse(Long episodeId, Integer episodeNumber, String title, String status) {
        this.episodeId = episodeId;
        this.episodeNumber = episodeNumber;
        this.title = title;
        this.status = status;
    }

    public static EpisodeResponse from(Episode episode) {
        return new EpisodeResponse(episode.getEpisodeId(), episode.getEpisodeNumber(),
                episode.getTitle(), episode.getStatus().name());
    }

    public Long getEpisodeId() { return episodeId; }
    public Integer getEpisodeNumber() { return episodeNumber; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
}
