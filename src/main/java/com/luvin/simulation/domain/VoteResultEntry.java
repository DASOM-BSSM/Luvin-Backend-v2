package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부(AI)에서 계산된 전체 라운드 투표 결과 한 줄(누가 누굴 골랐는지).
 */
@Entity
@Table(name = "vote_result_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteResultEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long entryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private EpisodeParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voted_for_participant_id", nullable = false)
    private EpisodeParticipant votedForParticipant;

    public VoteResultEntry(Episode episode, EpisodeParticipant participant, EpisodeParticipant votedForParticipant) {
        this.episode = episode;
        this.participant = participant;
        this.votedForParticipant = votedForParticipant;
    }
}
