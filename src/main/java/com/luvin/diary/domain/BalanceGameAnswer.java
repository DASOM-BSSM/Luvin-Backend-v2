package com.luvin.diary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 밸런스 게임 답. 한 사람당 질문 하나에 1개이고 바꿀 수 없다((member_id, daily_question_id) UNIQUE).
 * 테이블은 예전 daily_question_answers를 그대로 쓴다. 행은 BalanceGameAnswerRepository의 native insert로 만든다.
 */
@Entity
@Table(name = "daily_question_answers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "daily_question_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceGameAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "daily_question_id", nullable = false)
    private Long questionId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    /** 답한 시각(KST). */
    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;
}
