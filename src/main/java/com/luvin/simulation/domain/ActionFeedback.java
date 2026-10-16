package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자가 1:1 대화에서 자신의 AI 분신이 한 말을 골라 남긴 행동 피드백. 같은 메시지에는 사용자별로
 * 피드백이 하나만 유지되도록 member_id + message_id에 유니크 제약을 두고, 다시 남기면 덮어쓴다.
 */
@Entity
@Table(name = "action_feedbacks", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "message_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long feedbackId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private OneOnOneMessage message;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 20)
    private FeedbackType feedbackType;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ActionFeedback(Long memberId, Episode episode, OneOnOneMessage message,
                          FeedbackType feedbackType, String content) {
        this.memberId = memberId;
        this.episode = episode;
        this.message = message;
        this.feedbackType = feedbackType;
        this.content = content;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(FeedbackType feedbackType, String content) {
        this.feedbackType = feedbackType;
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }
}
