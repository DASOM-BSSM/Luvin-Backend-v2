package com.luvin.simulation.dto;

import com.luvin.simulation.domain.EpisodeGame;

public class GameResponse {
    private final Long gameId;
    private final Integer gameOrder;
    private final String title;

    public GameResponse(Long gameId, Integer gameOrder, String title) {
        this.gameId = gameId;
        this.gameOrder = gameOrder;
        this.title = title;
    }

    public static GameResponse from(EpisodeGame game) {
        return new GameResponse(game.getGameId(), game.getGameOrder(), game.getTitle());
    }

    public Long getGameId() { return gameId; }
    public Integer getGameOrder() { return gameOrder; }
    public String getTitle() { return title; }
}
