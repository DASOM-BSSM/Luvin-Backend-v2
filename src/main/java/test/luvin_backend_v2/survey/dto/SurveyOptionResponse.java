package com.luvin.survey.dto;

import java.util.List;

public class SurveyOptionResponse {
    private final Long questionId;
    private final String question;
    private final List<OptionItem> options;

    public SurveyOptionResponse(Long questionId, String question, List<OptionItem> options) {
        this.questionId = questionId;
        this.question = question;
        this.options = options;
    }

    public Long getQuestionId() { return questionId; }
    public String getQuestion() { return question; }
    public List<OptionItem> getOptions() { return options; }

    public static class OptionItem {
        private final Long optionId;
        private final String content;

        public OptionItem(Long optionId, String content) {
            this.optionId = optionId;
            this.content = content;
        }

        public Long getOptionId() { return optionId; }
        public String getContent() { return content; }
    }
}