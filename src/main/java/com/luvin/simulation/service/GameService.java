package com.luvin.simulation.service;

import com.luvin.simulation.dto.GameResponse;
import com.luvin.simulation.dto.GameResultRequest;

import java.util.List;

public interface GameService {
    List<GameResponse> getGames();
    void saveGameResult(Long memberId, Long episodeId, Long gameId, GameResultRequest request);
}
