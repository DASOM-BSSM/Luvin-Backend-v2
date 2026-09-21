package com.luvin.simulation.controller;

import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.EpisodeCreateRequest;
import com.luvin.simulation.dto.EpisodeResponse;
import com.luvin.simulation.dto.ParticipantResponse;
import com.luvin.simulation.service.EpisodeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/episodes")
public class EpisodeController {

    private final EpisodeService episodeService;

    public EpisodeController(EpisodeService episodeService) {
        this.episodeService = episodeService;
    }

    @GetMapping
    public List<EpisodeResponse> getEpisodes() {
        return episodeService.getEpisodes(SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/{episodeId}")
    public EpisodeResponse getEpisode(@PathVariable Long episodeId) {
        return episodeService.getEpisode(SecurityUtils.getCurrentUserId(), episodeId);
    }

    @PostMapping
    public EpisodeResponse createEpisode(@RequestBody EpisodeCreateRequest body) {
        return episodeService.createEpisode(SecurityUtils.getCurrentUserId(), body);
    }

    @GetMapping("/{episodeId}/participants")
    public List<ParticipantResponse> getParticipants(@PathVariable Long episodeId) {
        return episodeService.getParticipants(SecurityUtils.getCurrentUserId(), episodeId);
    }
}
