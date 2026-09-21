package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "highlights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Highlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "highlight_id")
    private Long highlightId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(nullable = false)
    private Integer importance;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Highlight(Long memberId, Episode episode, String title, String summary, Integer importance) {
        this.memberId = memberId;
        this.episode = episode;
        this.title = title;
        this.summary = summary;
        this.importance = importance;
        this.createdAt = LocalDateTime.now();
    }
}
