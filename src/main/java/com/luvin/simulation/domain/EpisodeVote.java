package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유저 본인의 투표(내가 고른 빵).
 */
@Entity
@Table(name = "episode_votes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EpisodeVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id")
    private Long voteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_participant_id", nullable = false)
    private EpisodeParticipant selectedParticipant;

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;

    public EpisodeVote(Episode episode, EpisodeParticipant selectedParticipant) {
        this.episode = episode;
        this.selectedParticipant = selectedParticipant;
        this.votedAt = LocalDateTime.now();
    }
}
