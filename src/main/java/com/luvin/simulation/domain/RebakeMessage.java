package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * "다시 굽기" 대안 1:1 대화 메시지.
 * alternativeParticipant = 그때 고르지 않았던 후보 빵 (이 대화가 어떤 후보에 대한 것인지).
 * speaker는 항상 SELF 아니면 alternativeParticipant 둘 중 하나여야 한다 (서비스 계층에서 검증).
 */
@Entity
@Table(name = "rebake_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RebakeMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alternative_participant_id", nullable = false)
    private EpisodeParticipant alternativeParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "speaker_participant_id", nullable = false)
    private EpisodeParticipant speaker;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public RebakeMessage(Episode episode, EpisodeParticipant alternativeParticipant,
                          EpisodeParticipant speaker, String content, Integer sequence) {
        this.episode = episode;
        this.alternativeParticipant = alternativeParticipant;
        this.speaker = speaker;
        this.content = content;
        this.sequence = sequence;
        this.createdAt = LocalDateTime.now();
    }
}
