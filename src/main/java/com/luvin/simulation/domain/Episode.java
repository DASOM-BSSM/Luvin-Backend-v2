package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "episodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "episode_id")
    private Long episodeId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EpisodeStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Episode(Long memberId, Integer episodeNumber, String title) {
        this.memberId = memberId;
        this.episodeNumber = episodeNumber;
        this.title = title;
        this.status = EpisodeStatus.IN_PROGRESS;
        this.createdAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = EpisodeStatus.COMPLETED;
    }
}
