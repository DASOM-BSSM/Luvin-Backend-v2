package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 에피소드 투표에서 유저의 AI 분신이 상대를 고른 근거. 에피소드당 1건만 존재한다.
 */
@Entity
@Table(name = "vote_reasons", uniqueConstraints = @UniqueConstraint(columnNames = {"episode_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reason_id")
    private Long reasonId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Column(nullable = false, length = 1000)
    private String reason;

    @ElementCollection
    @CollectionTable(name = "vote_reason_based_on", joinColumns = @JoinColumn(name = "reason_id"))
    @Column(name = "based_on", length = 100)
    private List<String> basedOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public VoteReason(Episode episode, String reason, List<String> basedOn) {
        this.episode = episode;
        this.reason = reason;
        this.basedOn = basedOn;
        this.createdAt = LocalDateTime.now();
    }
}
