package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "one_on_one_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OneOnOneMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "speaker_participant_id", nullable = false)
    private EpisodeParticipant speaker;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public OneOnOneMessage(Match match, EpisodeParticipant speaker, String content, Integer sequence) {
        this.match = match;
        this.speaker = speaker;
        this.content = content;
        this.sequence = sequence;
        this.createdAt = LocalDateTime.now();
    }
}
