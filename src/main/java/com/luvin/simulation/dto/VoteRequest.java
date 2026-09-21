package com.luvin.simulation.dto;

public class VoteRequest {
    private Long selectedParticipantId;

    public VoteRequest() {}

    public Long getSelectedParticipantId() { return selectedParticipantId; }
    public void setSelectedParticipantId(Long selectedParticipantId) { this.selectedParticipantId = selectedParticipantId; }
}
