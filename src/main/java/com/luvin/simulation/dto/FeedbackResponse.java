package com.luvin.simulation.dto;

public class FeedbackResponse {
    private final Long feedbackId;
    private final Long messageId;
    private final String message;

    public FeedbackResponse(Long feedbackId, Long messageId, String message) {
        this.feedbackId = feedbackId;
        this.messageId = messageId;
        this.message = message;
    }

    public Long getFeedbackId() { return feedbackId; }
    public Long getMessageId() { return messageId; }
    public String getMessage() { return message; }
}
