package com.luvin.simulation.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.ConversationMessageResponse;
import com.luvin.simulation.dto.ConversationSaveRequest;
import com.luvin.simulation.service.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/episodes/{episodeId}/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<ConversationMessageResponse> getConversations(@PathVariable Long episodeId) {
        return conversationService.getConversations(SecurityUtils.getCurrentUserId(), episodeId);
    }

    @PostMapping
    public MessageResponse saveConversations(@PathVariable Long episodeId, @RequestBody ConversationSaveRequest body) {
        conversationService.saveConversations(SecurityUtils.getCurrentUserId(), episodeId, body);
        return new MessageResponse("대화 저장 완료");
    }
}
