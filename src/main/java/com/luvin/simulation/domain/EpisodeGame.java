package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고정 2개짜리 게임(순서대로 노출). 에피소드와 무관한 전역 데이터.
 */
@Entity
@Table(name = "episode_games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EpisodeGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "game_order", nullable = false)
    private Integer gameOrder;

    @Column(nullable = false, length = 100)
    private String title;

    public EpisodeGame(Integer gameOrder, String title) {
        this.gameOrder = gameOrder;
        this.title = title;
    }
}
