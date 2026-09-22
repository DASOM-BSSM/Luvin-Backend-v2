package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.common.exception.GameNotFoundException;
import com.luvin.simulation.domain.Episode;
import com.luvin.simulation.domain.EpisodeGame;
import com.luvin.simulation.domain.EpisodeGameResult;
import com.luvin.simulation.dto.GameResponse;
import com.luvin.simulation.dto.GameResultRequest;
import com.luvin.simulation.repository.EpisodeGameRepository;
import com.luvin.simulation.repository.EpisodeGameResultRepository;
import com.luvin.simulation.repository.EpisodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final EpisodeGameRepository episodeGameRepository;
    private final EpisodeGameResultRepository episodeGameResultRepository;
    private final EpisodeRepository episodeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<GameResponse> getGames() {
        return episodeGameRepository.findAllByOrderByGameOrderAsc().stream()
                .map(GameResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveGameResult(Long memberId, Long episodeId, Long gameId, GameResultRequest request) {
        Episode episode = episodeRepository.findByEpisodeIdAndMemberId(episodeId, memberId)
                .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
        EpisodeGame game = episodeGameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
        episodeGameResultRepository.save(new EpisodeGameResult(episode, game, request.isSuccess()));
    }
}
