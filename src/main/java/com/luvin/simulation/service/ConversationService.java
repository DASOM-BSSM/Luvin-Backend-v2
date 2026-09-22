package com.luvin.simulation.service;

import com.luvin.simulation.dto.ConversationMessageResponse;
import com.luvin.simulation.dto.ConversationSaveRequest;

import java.util.List;

public interface ConversationService {
    List<ConversationMessageResponse> getConversations(Long memberId, Long episodeId);
    void saveConversations(Long memberId, Long episodeId, ConversationSaveRequest request);
}
