package com.luvin.dailyquestion.dto;

public class DailyQuestionOptionResultResponse {
    private final Long optionId;
    private final String content;
    private final long voteCount;
    private final double percentage;

    public DailyQuestionOptionResultResponse(Long optionId, String content, long voteCount, double percentage) {
        this.optionId = optionId;
        this.content = content;
        this.voteCount = voteCount;
        this.percentage = percentage;
    }

    public Long getOptionId() { return optionId; }
    public String getContent() { return content; }
    public long getVoteCount() { return voteCount; }
    public double getPercentage() { return percentage; }
}