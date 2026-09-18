package com.luvin.dailyquestion.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "daily_questions")
public class DailyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_question_id")
    private Long dailyQuestionId;

    @Column(nullable = false, length = 300)
    private String content;

    @OneToMany(mappedBy = "dailyQuestion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("optionId ASC")
    private List<DailyQuestionOption> options = new ArrayList<>();

    protected DailyQuestion() {
    }

    public DailyQuestion(String content) {
        this.content = content;
    }

    public Long getDailyQuestionId() { return dailyQuestionId; }
    public String getContent() { return content; }
    public List<DailyQuestionOption> getOptions() { return options; }
}