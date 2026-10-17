package com.luvin.simulation.controller;

import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.VoteReasonResponse;
import com.luvin.simulation.service.VoteReasonService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/episodes/{episodeId}/ai-situations/reason")
public class VoteReasonController {

    private final VoteReasonService voteReasonService;

    public VoteReasonController(VoteReasonService voteReasonService) {
        this.voteReasonService = voteReasonService;
    }

    @GetMapping
    public VoteReasonResponse getVoteReason(@PathVariable Long episodeId) {
        return voteReasonService.getVoteReason(SecurityUtils.getCurrentUserId(), episodeId);
    }
}
