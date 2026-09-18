package com.luvin.dailyquestion.dto;

public class DailyQuestionOptionResponse {
    private final Long optionId;
    private final String content;

    public DailyQuestionOptionResponse(Long optionId, String content) {
        this.optionId = optionId;
        this.content = content;
    }

    public Long getOptionId() { return optionId; }
    public String getContent() { return content; }
}