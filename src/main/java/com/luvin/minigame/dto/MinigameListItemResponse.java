package com.luvin.minigame.dto;

import com.luvin.minigame.domain.Minigame;

public class MinigameListItemResponse {
    private final Long gameId;
    private final String code;
    private final String title;
    private final int rewardToken;

    public MinigameListItemResponse(Long gameId, String code, String title, int rewardToken) {
        this.gameId = gameId;
        this.code = code;
        this.title = title;
        this.rewardToken = rewardToken;
    }

    public static MinigameListItemResponse from(Minigame minigame) {
        return new MinigameListItemResponse(
                minigame.getGameId(), minigame.getCode(), minigame.getTitle(), minigame.getRewardToken());
    }

    public Long getGameId() { return gameId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public int getRewardToken() { return rewardToken; }
}
