package com.luvin.simulation.dto;

import com.luvin.simulation.domain.EpisodeParticipant;

public class ParticipantResponse {
    private final Long participantId;
    private final String name;
    private final String gender;

    public ParticipantResponse(Long participantId, String name, String gender) {
        this.participantId = participantId;
        this.name = name;
        this.gender = gender;
    }

    public static ParticipantResponse from(EpisodeParticipant participant) {
        return new ParticipantResponse(
                participant.getParticipantId(), participant.getName(), participant.getGender());
    }

    public Long getParticipantId() { return participantId; }
    public String getName() { return name; }
    public String getGender() { return gender; }
}
