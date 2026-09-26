package com.luvin.simulation.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.ConversationMessageResponse;
import com.luvin.simulation.dto.ConversationSaveRequest;
import com.luvin.simulation.dto.MatchResponse;
import com.luvin.simulation.service.MatchService;
import com.luvin.simulation.service.MessageLikeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class MatchController {

    private final MatchService matchService;
    private final MessageLikeService messageLikeService;

    public MatchController(MatchService matchService, MessageLikeService messageLikeService) {
        this.matchService = matchService;
        this.messageLikeService = messageLikeService;
    }

    @GetMapping("/api/episodes/{episodeId}/matches")
    public List<MatchResponse> getMatches(@PathVariable Long episodeId) {
        return matchService.getMatches(SecurityUtils.getCurrentUserId(), episodeId);
    }

    @GetMapping("/api/matches/final-couple")
    public MatchResponse getFinalCouple() {
        return matchService.getFinalCouple(SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/api/matches/{matchId}/conversations")
    public List<ConversationMessageResponse> getOneOnOneConversation(@PathVariable Long matchId) {
        return matchService.getOneOnOneConversation(SecurityUtils.getCurrentUserId(), matchId);
    }

    @PostMapping("/api/matches/{matchId}/conversations")
    public MessageResponse saveOneOnOneConversation(@PathVariable Long matchId,
                                                      @RequestBody ConversationSaveRequest body) {
        matchService.saveOneOnOneConversation(SecurityUtils.getCurrentUserId(), matchId, body);
        return new MessageResponse("1:1 대화 저장 완료");
    }

    @PostMapping("/api/matches/{matchId}/conversations/{messageId}/like")
    public MessageResponse likeMessage(@PathVariable Long matchId, @PathVariable Long messageId) {
        messageLikeService.likeMessage(SecurityUtils.getCurrentUserId(), matchId, messageId);
        return new MessageResponse("하트 저장 완료");
    }
}
