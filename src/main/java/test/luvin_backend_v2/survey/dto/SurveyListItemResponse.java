package com.luvin.survey.dto;

import com.luvin.survey.Survey;

public class SurveyListItemResponse {
    private final Long surveyId;
    private final String title;

    public SurveyListItemResponse(Long surveyId, String title) {
        this.surveyId = surveyId;
        this.title = title;
    }

    public static SurveyListItemResponse from(Survey survey) {
        return new SurveyListItemResponse(survey.getSurveyId(), survey.getTitle());
    }

    public Long getSurveyId() { return surveyId; }
    public String getTitle() { return title; }
}