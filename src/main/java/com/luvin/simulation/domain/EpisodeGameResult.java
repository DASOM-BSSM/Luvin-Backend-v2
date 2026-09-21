package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "episode_game_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EpisodeGameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long resultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private EpisodeGame game;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt;

    public EpisodeGameResult(Episode episode, EpisodeGame game, boolean success) {
        this.episode = episode;
        this.game = game;
        this.success = success;
        this.playedAt = LocalDateTime.now();
    }
}
