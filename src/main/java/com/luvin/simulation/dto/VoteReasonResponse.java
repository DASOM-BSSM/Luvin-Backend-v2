package com.luvin.simulation.dto;

import com.luvin.simulation.domain.VoteReason;

import java.util.List;

public class VoteReasonResponse {
    private final Long episodeId;
    private final String reason;
    private final List<String> basedOn;

    public VoteReasonResponse(Long episodeId, String reason, List<String> basedOn) {
        this.episodeId = episodeId;
        this.reason = reason;
        this.basedOn = basedOn;
    }

    public static VoteReasonResponse from(VoteReason voteReason) {
        return new VoteReasonResponse(voteReason.getEpisode().getEpisodeId(),
                voteReason.getReason(), List.copyOf(voteReason.getBasedOn()));
    }

    public Long getEpisodeId() { return episodeId; }
    public String getReason() { return reason; }
    public List<String> getBasedOn() { return basedOn; }
}
