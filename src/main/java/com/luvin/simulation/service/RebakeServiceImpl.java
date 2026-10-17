package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.common.exception.ParticipantNotFoundException;
import com.luvin.common.exception.RebakeNotFoundException;
import com.luvin.simulation.domain.Episode;
import com.luvin.simulation.domain.EpisodeParticipant;
import com.luvin.simulation.domain.ParticipantType;
import com.luvin.simulation.domain.RebakeMessage;
import com.luvin.simulation.dto.ConversationMessageResponse;
import com.luvin.simulation.dto.ConversationSaveRequest;
import com.luvin.simulation.dto.RebakeSaveRequest;
import com.luvin.simulation.repository.EpisodeParticipantRepository;
import com.luvin.simulation.repository.EpisodeRepository;
import com.luvin.simulation.repository.RebakeMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RebakeServiceImpl implements RebakeService {

    private final EpisodeRepository episodeRepository;
    private final EpisodeParticipantRepository episodeParticipantRepository;
    private final RebakeMessageRepository rebakeMessageRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> getRebakeConversation(Long memberId, Long episodeId, Long participantId) {
        Episode episode = getEpisodeOrThrow(memberId, episodeId);
        List<RebakeMessage> messages = rebakeMessageRepository
                .findAllByEpisode_EpisodeIdAndAlternativeParticipant_ParticipantIdOrderBySequenceAsc(
                        episode.getEpisodeId(), participantId);
        if (messages.isEmpty()) {
            throw new RebakeNotFoundException(episodeId, participantId);
        }
        return messages.stream()
                .map(m -> new ConversationMessageResponse(
                        m.getMessageId(), m.getSequence(), m.getSpeaker().getParticipantId(), m.getSpeaker().getName(), m.getContent()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveRebakeConversations(Long memberId, Long episodeId, RebakeSaveRequest request) {
        Episode episode = getEpisodeOrThrow(memberId, episodeId);
        EpisodeParticipant self = episodeParticipantRepository
                .findFirstByEpisode_EpisodeIdAndParticipantType(episode.getEpisodeId(), ParticipantType.SELF)
                .orElseThrow(() -> new ParticipantNotFoundException(null));

        for (RebakeSaveRequest.OutcomeItem outcome : request.getOutcomes()) {
            EpisodeParticipant alternative = episodeParticipantRepository
                    .findByParticipantIdAndEpisode_EpisodeId(outcome.getParticipantId(), episode.getEpisodeId())
                    .orElseThrow(() -> new ParticipantNotFoundException(outcome.getParticipantId()));

            for (ConversationSaveRequest.MessageItem item : outcome.getMessages()) {
                EpisodeParticipant speaker = episodeParticipantRepository
                        .findByParticipantIdAndEpisode_EpisodeId(item.getSpeakerParticipantId(), episode.getEpisodeId())
                        .orElseThrow(() -> new ParticipantNotFoundException(item.getSpeakerParticipantId()));

                // 다시 굽기 대화는 무조건 "나"와 "그 대안 빵" 둘만 등장해야 한다.
                boolean isSelfOrAlternative = speaker.getParticipantId().equals(self.getParticipantId())
                        || speaker.getParticipantId().equals(alternative.getParticipantId());
                if (!isSelfOrAlternative) {
                    throw new ParticipantNotFoundException(item.getSpeakerParticipantId());
                }

                rebakeMessageRepository.save(
                        new RebakeMessage(episode, alternative, speaker, item.getContent(), item.getSequence()));
            }
        }
    }

    private Episode getEpisodeOrThrow(Long memberId, Long episodeId) {
        return episodeRepository.findByEpisodeIdAndMemberId(episodeId, memberId)
                .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
    }
}
