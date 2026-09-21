package com.luvin.minigame.service;

import com.luvin.common.exception.MinigameNotFoundException;
import com.luvin.minigame.domain.Minigame;
import com.luvin.minigame.domain.MinigamePlayResult;
import com.luvin.minigame.dto.MinigameDetailResponse;
import com.luvin.minigame.dto.MinigameListItemResponse;
import com.luvin.minigame.dto.MinigameListResponse;
import com.luvin.minigame.dto.MinigamePlayRequest;
import com.luvin.minigame.dto.MinigamePlayResponse;
import com.luvin.minigame.repository.MinigamePlayResultRepository;
import com.luvin.minigame.repository.MinigameRepository;
import com.luvin.token.service.TokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MinigameServiceImpl implements MinigameService {

    private final MinigameRepository minigameRepository;
    private final MinigamePlayResultRepository minigamePlayResultRepository;
    private final TokenService tokenService;

    public MinigameServiceImpl(MinigameRepository minigameRepository,
                                MinigamePlayResultRepository minigamePlayResultRepository,
                                TokenService tokenService) {
        this.minigameRepository = minigameRepository;
        this.minigamePlayResultRepository = minigamePlayResultRepository;
        this.tokenService = tokenService;
    }

    @Override
    @Transactional(readOnly = true)
    public MinigameListResponse getMinigames() {
        List<MinigameListItemResponse> games = minigameRepository.findAll().stream()
                .map(MinigameListItemResponse::from)
                .collect(Collectors.toList());
        return new MinigameListResponse(games);
    }

    @Override
    @Transactional(readOnly = true)
    public MinigameDetailResponse getMinigameDetail(Long gameId) {
        Minigame minigame = getMinigameOrThrow(gameId);
        return MinigameDetailResponse.from(minigame);
    }

    @Override
    @Transactional
    public MinigamePlayResponse play(Long memberId, Long gameId, MinigamePlayRequest request) {
        Minigame minigame = getMinigameOrThrow(gameId);

        boolean success = request.isSuccess();
        int earnedToken = success ? minigame.getRewardToken() : 0;

        minigamePlayResultRepository.save(new MinigamePlayResult(memberId, minigame, success, earnedToken));

        if (earnedToken > 0) {
            tokenService.grantToken(memberId, earnedToken, "미니게임 보상: " + minigame.getTitle());
        }

        return new MinigamePlayResponse(minigame.getGameId(), new MinigamePlayResponse.Reward(earnedToken));
    }

    private Minigame getMinigameOrThrow(Long gameId) {
        return minigameRepository.findById(gameId)
                .orElseThrow(() -> new MinigameNotFoundException(gameId));
    }
}
