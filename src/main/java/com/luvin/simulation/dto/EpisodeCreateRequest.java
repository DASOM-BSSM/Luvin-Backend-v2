package com.luvin.simulation.dto;

public class EpisodeCreateRequest {
    private Integer episodeNumber;
    private String title;

    public EpisodeCreateRequest() {}

    public Integer getEpisodeNumber() { return episodeNumber; }
    public String getTitle() { return title; }
    public void setEpisodeNumber(Integer episodeNumber) { this.episodeNumber = episodeNumber; }
    public void setTitle(String title) { this.title = title; }
}
