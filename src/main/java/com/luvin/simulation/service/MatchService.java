package com.luvin.simulation.service;

import com.luvin.simulation.dto.ConversationMessageResponse;
import com.luvin.simulation.dto.ConversationSaveRequest;
import com.luvin.simulation.dto.MatchResponse;

import java.util.List;

public interface MatchService {
    List<MatchResponse> getMatches(Long memberId, Long episodeId);
    MatchResponse getFinalCouple(Long memberId);
    List<ConversationMessageResponse> getOneOnOneConversation(Long memberId, Long matchId);
    void saveOneOnOneConversation(Long memberId, Long matchId, ConversationSaveRequest request);
}
