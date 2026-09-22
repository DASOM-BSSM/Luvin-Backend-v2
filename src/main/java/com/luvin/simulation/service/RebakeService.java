package com.luvin.simulation.service;

import com.luvin.simulation.dto.ConversationMessageResponse;
import com.luvin.simulation.dto.RebakeSaveRequest;

import java.util.List;

public interface RebakeService {
    List<ConversationMessageResponse> getRebakeConversation(Long memberId, Long episodeId, Long participantId);
    void saveRebakeConversations(Long memberId, Long episodeId, RebakeSaveRequest request);
}
