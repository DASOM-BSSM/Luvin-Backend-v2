package com.luvin.survey;

import jakarta.persistence.*;

@Entity
@Table(name = "surveys")
public class Survey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "survey_id")
    private Long surveyId;

    @Column(nullable = false, length = 200)
    private String title;

    protected Survey() {
    }

    public Survey(String title) {
        this.title = title;
    }

    public Long getSurveyId() { return surveyId; }
    public String getTitle() { return title; }
}