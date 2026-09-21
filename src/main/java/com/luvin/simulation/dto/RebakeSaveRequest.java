package com.luvin.simulation.dto;

import java.util.List;

public class RebakeSaveRequest {
    private List<OutcomeItem> outcomes;

    public RebakeSaveRequest() {}

    public List<OutcomeItem> getOutcomes() { return outcomes; }
    public void setOutcomes(List<OutcomeItem> outcomes) { this.outcomes = outcomes; }

    public static class OutcomeItem {
        private Long participantId;
        private List<ConversationSaveRequest.MessageItem> messages;

        public OutcomeItem() {}

        public Long getParticipantId() { return participantId; }
        public List<ConversationSaveRequest.MessageItem> getMessages() { return messages; }
        public void setParticipantId(Long participantId) { this.participantId = participantId; }
        public void setMessages(List<ConversationSaveRequest.MessageItem> messages) { this.messages = messages; }
    }
}
