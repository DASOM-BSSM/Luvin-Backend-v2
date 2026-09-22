package com.luvin.simulation.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.ConversationMessageResponse;
import com.luvin.simulation.dto.RebakeSaveRequest;
import com.luvin.simulation.service.RebakeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/episodes/{episodeId}/rebakes")
public class RebakeController {

    private final RebakeService rebakeService;

    public RebakeController(RebakeService rebakeService) {
        this.rebakeService = rebakeService;
    }

    @GetMapping("/{participantId}")
    public List<ConversationMessageResponse> getRebakeConversation(@PathVariable Long episodeId,
                                                                     @PathVariable Long participantId) {
        return rebakeService.getRebakeConversation(SecurityUtils.getCurrentUserId(), episodeId, participantId);
    }

    @PostMapping
    public MessageResponse saveRebakeConversations(@PathVariable Long episodeId, @RequestBody RebakeSaveRequest body) {
        rebakeService.saveRebakeConversations(SecurityUtils.getCurrentUserId(), episodeId, body);
        return new MessageResponse("다시 굽기 결과 저장 완료");
    }
}
