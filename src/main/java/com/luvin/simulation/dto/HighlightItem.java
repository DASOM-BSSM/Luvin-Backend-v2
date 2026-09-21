package com.luvin.simulation.dto;

public class HighlightItem {
    private Long episodeId;
    private String title;
    private String summary;
    private Integer importance;

    public HighlightItem() {}

    public HighlightItem(Long episodeId, String title, String summary, Integer importance) {
        this.episodeId = episodeId;
        this.title = title;
        this.summary = summary;
        this.importance = importance;
    }

    public Long getEpisodeId() { return episodeId; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public Integer getImportance() { return importance; }
    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }
    public void setTitle(String title) { this.title = title; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setImportance(Integer importance) { this.importance = importance; }
}
