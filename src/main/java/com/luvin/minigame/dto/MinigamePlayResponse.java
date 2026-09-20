package com.luvin.minigame.dto;

public class MinigamePlayResponse {
    private final Long gameId;
    private final Reward reward;

    public MinigamePlayResponse(Long gameId, Reward reward) {
        this.gameId = gameId;
        this.reward = reward;
    }

    public Long getGameId() { return gameId; }
    public Reward getReward() { return reward; }

    public static class Reward {
        private final int token;

        public Reward(int token) {
            this.token = token;
        }

        public int getToken() { return token; }
    }
}
