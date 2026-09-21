package com.luvin.simulation.service;

import com.luvin.simulation.dto.ParticipantResponse;
import com.luvin.simulation.dto.VoteRequest;
import com.luvin.simulation.dto.VoteResultData;

import java.util.List;

public interface VoteService {
    List<ParticipantResponse> getVoteOptions(Long memberId, Long episodeId);
    void vote(Long memberId, Long episodeId, VoteRequest request);
    VoteResultData getVoteResults(Long memberId, Long episodeId);
    void saveVoteResults(Long memberId, Long episodeId, VoteResultData request);
}
