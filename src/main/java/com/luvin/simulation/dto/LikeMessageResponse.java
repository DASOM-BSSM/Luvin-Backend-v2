package com.luvin.simulation.dto;

public class LikeMessageResponse {
    private final Integer episodeNumber;
    private final Long messageId;
    private final String text;

    public LikeMessageResponse(Integer episodeNumber, Long messageId, String text) {
        this.episodeNumber = episodeNumber;
        this.messageId = messageId;
        this.text = text;
    }

    public Integer getEpisodeNumber() { return episodeNumber; }
    public Long getMessageId() { return messageId; }
    public String getText() { return text; }
}
