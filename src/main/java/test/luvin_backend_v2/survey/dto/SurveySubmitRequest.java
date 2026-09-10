package com.luvin.survey.dto;

import java.util.List;

public class SurveySubmitRequest {
    private List<AnswerItem> answers;

    public SurveySubmitRequest() {}

    public List<AnswerItem> getAnswers() { return answers; }
    public void setAnswers(List<AnswerItem> answers) { this.answers = answers; }

    public static class AnswerItem {
        private Long questionId;
        private Long optionId;

        public AnswerItem() {}

        public Long getQuestionId() { return questionId; }
        public Long getOptionId() { return optionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public void setOptionId(Long optionId) { this.optionId = optionId; }
    }
}