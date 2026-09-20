package com.luvin.minigame.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "minigames")
public class Minigame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_id")
    private Long gameId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "reward_token", nullable = false)
    private int rewardToken;

    protected Minigame() {
    }

    public Minigame(String title, String description, String thumbnailUrl, int rewardToken) {
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.rewardToken = rewardToken;
    }

    public Long getGameId() { return gameId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getRewardToken() { return rewardToken; }
}
