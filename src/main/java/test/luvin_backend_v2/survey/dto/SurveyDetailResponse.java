package com.luvin.survey.dto;

import java.util.List;

public class SurveyDetailResponse {
    private final Long surveyId;
    private final String title;
    private final List<QuestionItem> questions;

    public SurveyDetailResponse(Long surveyId, String title, List<QuestionItem> questions) {
        this.surveyId = surveyId;
        this.title = title;
        this.questions = questions;
    }

    public Long getSurveyId() { return surveyId; }
    public String getTitle() { return title; }
    public List<QuestionItem> getQuestions() { return questions; }

    public static class QuestionItem {
        private final Long questionId;
        private final String question;

        public QuestionItem(Long questionId, String question) {
            this.questionId = questionId;
            this.question = question;
        }

        public Long getQuestionId() { return questionId; }
        public String getQuestion() { return question; }
    }
}