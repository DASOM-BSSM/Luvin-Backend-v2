package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long matchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_a_id", nullable = false)
    private EpisodeParticipant participantA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_b_id", nullable = false)
    private EpisodeParticipant participantB;

    @Column(name = "is_final", nullable = false)
    private boolean isFinal;

    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;

    public Match(Episode episode, EpisodeParticipant participantA, EpisodeParticipant participantB, boolean isFinal) {
        this.episode = episode;
        this.participantA = participantA;
        this.participantB = participantB;
        this.isFinal = isFinal;
        this.matchedAt = LocalDateTime.now();
    }
}
