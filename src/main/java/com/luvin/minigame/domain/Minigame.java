package com.luvin.minigame.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "minigames")
public class Minigame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_id")
    private Long gameId;

    /**
     * title과 달리 바뀌지 않는 식별 키(예: "dinosaur_bread_game"). 앱이 title 문자열 비교로
     * gameId를 찾다가 title이 조금만 달라져도 보상이 통째로 안 나가는 문제가 있어 추가했다.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

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

    public Minigame(String code, String title, String description, String thumbnailUrl, int rewardToken) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.rewardToken = rewardToken;
    }

    public Long getGameId() { return gameId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getRewardToken() { return rewardToken; }
}
