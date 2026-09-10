package com.luvin.survey;

import jakarta.persistence.*;

@Entity
@Table(name = "survey_questions")
public class SurveyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(nullable = false, length = 300)
    private String content;

    protected SurveyQuestion() {
    }

    public SurveyQuestion(Survey survey, String content) {
        this.survey = survey;
        this.content = content;
    }

    public Long getQuestionId() { return questionId; }
    public Survey getSurvey() { return survey; }
    public String getContent() { return content; }
}