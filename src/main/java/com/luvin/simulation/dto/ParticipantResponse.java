package com.luvin.simulation.dto;

import com.luvin.simulation.domain.EpisodeParticipant;

public class ParticipantResponse {
    private final Long participantId;
    private final String name;

    public ParticipantResponse(Long participantId, String name) {
        this.participantId = participantId;
        this.name = name;
    }

    public static ParticipantResponse from(EpisodeParticipant participant) {
        return new ParticipantResponse(participant.getParticipantId(), participant.getName());
    }

    public Long getParticipantId() { return participantId; }
    public String getName() { return name; }
}
