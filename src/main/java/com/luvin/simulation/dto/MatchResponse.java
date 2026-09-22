package com.luvin.simulation.dto;

import com.luvin.simulation.domain.Match;

public class MatchResponse {
    private final Long matchId;
    private final Long participantAId;
    private final Long participantBId;
    private final boolean isFinal;

    public MatchResponse(Long matchId, Long participantAId, Long participantBId, boolean isFinal) {
        this.matchId = matchId;
        this.participantAId = participantAId;
        this.participantBId = participantBId;
        this.isFinal = isFinal;
    }

    public static MatchResponse from(Match match) {
        return new MatchResponse(match.getMatchId(), match.getParticipantA().getParticipantId(),
                match.getParticipantB().getParticipantId(), match.isFinal());
    }

    public Long getMatchId() { return matchId; }
    public Long getParticipantAId() { return participantAId; }
    public Long getParticipantBId() { return participantBId; }
    public boolean getIsFinal() { return isFinal; }
}
