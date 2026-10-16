package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.common.exception.NoteNotFoundException;
import com.luvin.simulation.dto.NoteResponse;
import com.luvin.simulation.repository.EpisodeRepository;
import com.luvin.simulation.repository.SimulationNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final EpisodeRepository episodeRepository;
    private final SimulationNoteRepository simulationNoteRepository;

    @Override
    @Transactional(readOnly = true)
    public NoteResponse getNote(Long memberId, Long episodeId) {
        // 타인의 에피소드 쪽지는 존재 여부도 노출하지 않는다.
        episodeRepository.findByEpisodeIdAndMemberId(episodeId, memberId)
                .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
        return simulationNoteRepository.findFirstByEpisode_EpisodeIdOrderByCreatedAtDesc(episodeId)
                .map(NoteResponse::from)
                .orElseThrow(() -> new NoteNotFoundException(episodeId));
    }
}
