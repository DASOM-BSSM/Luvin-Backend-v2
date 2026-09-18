package com.luvin.dailyquestion.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "daily_question_answers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "daily_question_id"})
)
public class DailyQuestionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_question_id", nullable = false)
    private DailyQuestion dailyQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private DailyQuestionOption selectedOption;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    protected DailyQuestionAnswer() {
    }

    public DailyQuestionAnswer(Long memberId, DailyQuestion dailyQuestion, DailyQuestionOption selectedOption) {
        this.memberId = memberId;
        this.dailyQuestion = dailyQuestion;
        this.selectedOption = selectedOption;
        this.answeredAt = LocalDateTime.now();
    }

    public Long getAnswerId() { return answerId; }
    public Long getMemberId() { return memberId; }
    public DailyQuestion getDailyQuestion() { return dailyQuestion; }
    public DailyQuestionOption getSelectedOption() { return selectedOption; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
}