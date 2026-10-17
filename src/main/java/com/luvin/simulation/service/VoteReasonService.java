package com.luvin.simulation.service;

import com.luvin.simulation.dto.VoteReasonResponse;

public interface VoteReasonService {
    VoteReasonResponse getVoteReason(Long memberId, Long episodeId);
}
