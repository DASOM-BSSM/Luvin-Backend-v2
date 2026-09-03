package com.luvin.survey;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "survey_answers")
public class SurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private SurveyQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private SurveyOption selectedOption;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    protected SurveyAnswer() {
    }

    public SurveyAnswer(Long memberId, SurveyQuestion question, SurveyOption selectedOption) {
        this.memberId = memberId;
        this.question = question;
        this.selectedOption = selectedOption;
        this.answeredAt = LocalDateTime.now();
    }

    public Long getAnswerId() { return answerId; }
    public Long getMemberId() { return memberId; }
    public SurveyQuestion getQuestion() { return question; }
    public SurveyOption getSelectedOption() { return selectedOption; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
}