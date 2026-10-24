package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 4~10화 회차 열기 기록(1~3화는 무료라 기록하지 않는다). 회차당 한 번만 토큰을 쓰고(season_id + episode_order unique),
 * 이미 연 회차는 다시 차감하지 않는다. 열고 나서 생성이 실패해도 열린 상태로 남는다.
 */
@Entity
@Table(name = "ai_episode_unlocks", uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "episode_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiEpisodeUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "episode_order", nullable = false)
    private Integer episodeOrder;

    /** 실제로 차감한 토큰 수(개발 서버에서 차감을 끄면 0). */
    @Column(name = "tokens_spent", nullable = false)
    private int tokensSpent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiEpisodeUnlock(AiSeason season, int episodeOrder, int tokensSpent) {
        this.season = season;
        this.episodeOrder = episodeOrder;
        this.tokensSpent = tokensSpent;
        this.createdAt = LocalDateTime.now();
    }
}
