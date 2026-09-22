package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.simulation.domain.Episode;
import com.luvin.simulation.domain.Highlight;
import com.luvin.simulation.dto.HighlightItem;
import com.luvin.simulation.dto.HighlightSaveRequest;
import com.luvin.simulation.repository.EpisodeRepository;
import com.luvin.simulation.repository.HighlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HighlightServiceImpl implements HighlightService {

    private final HighlightRepository highlightRepository;
    private final EpisodeRepository episodeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<HighlightItem> getHighlights(Long memberId) {
        return highlightRepository.findAllByMemberIdOrderByImportanceDesc(memberId).stream()
                .map(h -> new HighlightItem(h.getEpisode().getEpisodeId(), h.getTitle(), h.getSummary(), h.getImportance()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveHighlights(Long memberId, HighlightSaveRequest request) {
        for (HighlightItem item : request.getHighlights()) {
            Episode episode = episodeRepository.findByEpisodeIdAndMemberId(item.getEpisodeId(), memberId)
                    .orElseThrow(() -> new EpisodeNotFoundException(item.getEpisodeId()));
            highlightRepository.save(
                    new Highlight(memberId, episode, item.getTitle(), item.getSummary(), item.getImportance()));
        }
    }
}
