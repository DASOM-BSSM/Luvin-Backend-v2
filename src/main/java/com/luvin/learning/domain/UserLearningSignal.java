package com.luvin.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 사용자가 쓴 글 하나를 학습에 쓸 수 있다는 표시(Flyway V12). 원본(일기·댓글·그룹 답·밸런스 답)이 지워지면 함께 지운다.
 * 말투 학습과 성격 학습이 따로 소비한다(speechConsumedAt / personalityConsumedAt).
 * 행은 LearningSignalService가 native insert로만 만든다.
 */
@Entity
@Table(name = "user_learning_signal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLearningSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private LearningSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "speech_consumed_at")
    private OffsetDateTime speechConsumedAt;

    @Column(name = "personality_consumed_at")
    private OffsetDateTime personalityConsumedAt;
}
