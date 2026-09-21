package com.luvin.simulation.dto;

import java.util.List;

public class ConversationSaveRequest {
    private List<MessageItem> messages;

    public ConversationSaveRequest() {}

    public List<MessageItem> getMessages() { return messages; }
    public void setMessages(List<MessageItem> messages) { this.messages = messages; }

    public static class MessageItem {
        private Long speakerParticipantId;
        private String content;
        private Integer sequence;

        public MessageItem() {}

        public Long getSpeakerParticipantId() { return speakerParticipantId; }
        public String getContent() { return content; }
        public Integer getSequence() { return sequence; }
        public void setSpeakerParticipantId(Long speakerParticipantId) { this.speakerParticipantId = speakerParticipantId; }
        public void setContent(String content) { this.content = content; }
        public void setSequence(Integer sequence) { this.sequence = sequence; }
    }
}
