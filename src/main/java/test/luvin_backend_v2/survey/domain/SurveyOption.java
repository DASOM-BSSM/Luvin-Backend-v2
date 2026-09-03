package com.luvin.survey;

import jakarta.persistence.*;

@Entity
@Table(name = "survey_options")
public class SurveyOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private SurveyQuestion question;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(length = 500)
    private String effects;

    protected SurveyOption() {
    }

    public SurveyOption(SurveyQuestion question, String content) {
        this(question, content, null);
    }

    public SurveyOption(SurveyQuestion question, String content, String effects) {
        this.question = question;
        this.content = content;
        this.effects = effects;
    }

    public Long getOptionId() { return optionId; }
    public SurveyQuestion getQuestion() { return question; }
    public String getContent() { return content; }
    public String getEffects() { return effects; }
}
