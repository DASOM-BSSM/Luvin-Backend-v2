package com.luvin.minigame.controller;

import com.luvin.common.security.SecurityUtils;
import com.luvin.minigame.dto.MinigameDetailResponse;
import com.luvin.minigame.dto.MinigameListResponse;
import com.luvin.minigame.dto.MinigamePlayRequest;
import com.luvin.minigame.dto.MinigamePlayResponse;
import com.luvin.minigame.service.MinigameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/minigames")
public class MinigameController {

    private final MinigameService minigameService;

    public MinigameController(MinigameService minigameService) {
        this.minigameService = minigameService;
    }

    @GetMapping
    public MinigameListResponse getMinigames() {
        return minigameService.getMinigames();
    }

    @GetMapping("/{gameId}")
    public MinigameDetailResponse getMinigameDetail(@PathVariable Long gameId) {
        return minigameService.getMinigameDetail(gameId);
    }

    @PostMapping("/{gameId}/play")
    public MinigamePlayResponse play(@PathVariable Long gameId,
                                     @Valid @RequestBody MinigamePlayRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return minigameService.play(memberId, gameId, request);
    }
}
