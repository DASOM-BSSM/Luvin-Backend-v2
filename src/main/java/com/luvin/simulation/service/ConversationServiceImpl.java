package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.common.exception.ParticipantNotFoundException;
import com.luvin.simulation.domain.Episode;
import com.luvin.simulation.domain.EpisodeParticipant;
import com.luvin.simulation.domain.GroupConversationMessage;
import com.luvin.simulation.dto.ConversationMessageResponse;
import com.luvin.simulation.dto.ConversationSaveRequest;
import com.luvin.simulation.repository.EpisodeParticipantRepository;
import com.luvin.simulation.repository.EpisodeRepository;
import com.luvin.simulation.repository.GroupConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final EpisodeRepository episodeRepository;
    private final EpisodeParticipantRepository episodeParticipantRepository;
    private final GroupConversationMessageRepository groupConversationMessageRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> getConversations(Long memberId, Long episodeId) {
        Episode episode = getEpisodeOrThrow(memberId, episodeId);
        return groupConversationMessageRepository
                .findAllByEpisode_EpisodeIdOrderBySequenceAsc(episode.getEpisodeId()).stream()
                .map(m -> new ConversationMessageResponse(
                        m.getSequence(), m.getSpeaker().getParticipantId(), m.getSpeaker().getName(), m.getContent()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveConversations(Long memberId, Long episodeId, ConversationSaveRequest request) {
        Episode episode = getEpisodeOrThrow(memberId, episodeId);
        for (ConversationSaveRequest.MessageItem item : request.getMessages()) {
            EpisodeParticipant speaker = episodeParticipantRepository
                    .findByParticipantIdAndEpisode_EpisodeId(item.getSpeakerParticipantId(), episode.getEpisodeId())
                    .orElseThrow(() -> new ParticipantNotFoundException(item.getSpeakerParticipantId()));
            groupConversationMessageRepository.save(
                    new GroupConversationMessage(episode, speaker, item.getContent(), item.getSequence()));
        }
    }

    private Episode getEpisodeOrThrow(Long memberId, Long episodeId) {
        return episodeRepository.findByEpisodeIdAndMemberId(episodeId, memberId)
                .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
    }
}
