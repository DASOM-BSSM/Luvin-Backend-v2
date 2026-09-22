package com.luvin.simulation.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.GameResponse;
import com.luvin.simulation.dto.GameResultRequest;
import com.luvin.simulation.service.GameService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/api/games")
    public List<GameResponse> getGames() {
        return gameService.getGames();
    }

    @PostMapping("/api/episodes/{episodeId}/games/{gameId}/result")
    public MessageResponse saveGameResult(@PathVariable Long episodeId, @PathVariable Long gameId,
                                           @RequestBody GameResultRequest body) {
        gameService.saveGameResult(SecurityUtils.getCurrentUserId(), episodeId, gameId, body);
        return new MessageResponse("게임 결과 저장 완료");
    }
}
