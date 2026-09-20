package com.luvin.minigame.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "minigame_play_results")
public class MinigamePlayResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "play_result_id")
    private Long playResultId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Minigame minigame;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "earned_token", nullable = false)
    private int earnedToken;

    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt;

    protected MinigamePlayResult() {
    }

    public MinigamePlayResult(Long memberId, Minigame minigame, boolean success, int earnedToken) {
        this.memberId = memberId;
        this.minigame = minigame;
        this.success = success;
        this.earnedToken = earnedToken;
        this.playedAt = LocalDateTime.now();
    }

    public Long getPlayResultId() { return playResultId; }
    public Long getMemberId() { return memberId; }
    public Minigame getMinigame() { return minigame; }
    public boolean isSuccess() { return success; }
    public int getEarnedToken() { return earnedToken; }
    public LocalDateTime getPlayedAt() { return playedAt; }
}
