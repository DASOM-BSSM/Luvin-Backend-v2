package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자가 에피소드 내 1:1 대화에서 하트를 누른 메시지. 에피소드당 사용자별 하트는 최대 1개이며,
 * member_id + episode_id에 유니크 제약을 둬 DB 레벨에서도 중복을 막는다.
 */
@Entity
@Table(name = "message_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "episode_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long likeId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private OneOnOneMessage message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public MessageLike(Long memberId, Episode episode, OneOnOneMessage message) {
        this.memberId = memberId;
        this.episode = episode;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public void changeMessage(OneOnOneMessage message) {
        this.message = message;
    }
}
