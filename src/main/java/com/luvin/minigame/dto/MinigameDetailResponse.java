package com.luvin.minigame.dto;

import com.luvin.minigame.domain.Minigame;

public class MinigameDetailResponse {
    private final Long gameId;
    private final String title;
    private final String description;
    private final String thumbnailUrl;
    private final int rewardToken;

    public MinigameDetailResponse(Long gameId, String title, String description, String thumbnailUrl, int rewardToken) {
        this.gameId = gameId;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.rewardToken = rewardToken;
    }

    public static MinigameDetailResponse from(Minigame minigame) {
        return new MinigameDetailResponse(
                minigame.getGameId(),
                minigame.getTitle(),
                minigame.getDescription(),
                minigame.getThumbnailUrl(),
                minigame.getRewardToken()
        );
    }

    public Long getGameId() { return gameId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getRewardToken() { return rewardToken; }
}
