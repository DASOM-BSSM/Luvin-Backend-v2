package com.luvin.simulation.dto;

public class ConversationMessageResponse {
    private final Long messageId;
    private final Integer sequence;
    private final Long speakerParticipantId;
    private final String speakerName;
    private final String content;

    public ConversationMessageResponse(Long messageId, Integer sequence, Long speakerParticipantId,
                                         String speakerName, String content) {
        this.messageId = messageId;
        this.sequence = sequence;
        this.speakerParticipantId = speakerParticipantId;
        this.speakerName = speakerName;
        this.content = content;
    }

    public Long getMessageId() { return messageId; }
    public Integer getSequence() { return sequence; }
    public Long getSpeakerParticipantId() { return speakerParticipantId; }
    public String getSpeakerName() { return speakerName; }
    public String getContent() { return content; }
}
