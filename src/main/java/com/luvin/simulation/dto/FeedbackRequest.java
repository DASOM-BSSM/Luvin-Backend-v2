package com.luvin.simulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * feedbackType은 String으로 받아 서비스에서 FeedbackType으로 변환한다. enum으로 바로 받으면
 * 정의되지 않은 값이 역직렬화 단계에서 터져 400이 아닌 500으로 나가기 때문이다.
 */
public class FeedbackRequest {
    @NotNull(message = "messageId는 필수입니다.")
    private Long messageId;

    @NotBlank(message = "feedbackType은 필수입니다.")
    private String feedbackType;

    @NotBlank(message = "content는 필수입니다.")
    @Size(max = 1000, message = "content는 1000자 이하여야 합니다.")
    private String content;

    public FeedbackRequest() {}

    public Long getMessageId() { return messageId; }
    public String getFeedbackType() { return feedbackType; }
    public String getContent() { return content; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }
    public void setContent(String content) { this.content = content; }
}
