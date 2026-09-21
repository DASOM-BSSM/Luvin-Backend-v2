package com.luvin.simulation.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.ParticipantResponse;
import com.luvin.simulation.dto.VoteRequest;
import com.luvin.simulation.dto.VoteResultData;
import com.luvin.simulation.service.VoteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/episodes/{episodeId}")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @GetMapping("/vote-options")
    public List<ParticipantResponse> getVoteOptions(@PathVariable Long episodeId) {
        return voteService.getVoteOptions(SecurityUtils.getCurrentUserId(), episodeId);
    }

    @PostMapping("/vote")
    public MessageResponse vote(@PathVariable Long episodeId, @RequestBody VoteRequest body) {
        voteService.vote(SecurityUtils.getCurrentUserId(), episodeId, body);
        return new MessageResponse("투표 완료");
    }

    @GetMapping("/vote-results")
    public VoteResultData getVoteResults(@PathVariable Long episodeId) {
        return voteService.getVoteResults(SecurityUtils.getCurrentUserId(), episodeId);
    }

    @PostMapping("/vote-results")
    public MessageResponse saveVoteResults(@PathVariable Long episodeId, @RequestBody VoteResultData body) {
        voteService.saveVoteResults(SecurityUtils.getCurrentUserId(), episodeId, body);
        return new MessageResponse("투표 결과 저장 완료");
    }
}
