package com.luvin.dailyquestion.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "daily_question_options")
public class DailyQuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_question_id", nullable = false)
    private DailyQuestion dailyQuestion;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(length = 500)
    private String effects;

    protected DailyQuestionOption() {
    }

    public DailyQuestionOption(DailyQuestion dailyQuestion, String content) {
        this(dailyQuestion, content, null);
    }

    public DailyQuestionOption(DailyQuestion dailyQuestion, String content, String effects) {
        this.dailyQuestion = dailyQuestion;
        this.content = content;
        this.effects = effects;
    }

    public Long getOptionId() { return optionId; }
    public DailyQuestion getDailyQuestion() { return dailyQuestion; }
    public String getContent() { return content; }
    public String getEffects() { return effects; }
}