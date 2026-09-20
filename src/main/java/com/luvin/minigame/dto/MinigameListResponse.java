package com.luvin.minigame.dto;

import java.util.List;

public class MinigameListResponse {
    private final List<MinigameListItemResponse> games;

    public MinigameListResponse(List<MinigameListItemResponse> games) {
        this.games = games;
    }

    public List<MinigameListItemResponse> getGames() { return games; }
}
