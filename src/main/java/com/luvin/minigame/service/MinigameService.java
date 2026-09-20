package com.luvin.minigame.service;

import com.luvin.minigame.dto.MinigameDetailResponse;
import com.luvin.minigame.dto.MinigameListResponse;
import com.luvin.minigame.dto.MinigamePlayRequest;
import com.luvin.minigame.dto.MinigamePlayResponse;

public interface MinigameService {
    MinigameListResponse getMinigames();
    MinigameDetailResponse getMinigameDetail(Long gameId);
    MinigamePlayResponse play(Long memberId, Long gameId, MinigamePlayRequest request);
}
