package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.common.exception.MatchNotFoundException;
import com.luvin.common.exception.ParticipantNotFoundException;
import com.luvin.simulation.domain.EpisodeParticipant;
import com.luvin.simulation.domain.Match;
import com.luvin.simulation.domain.OneOnOneMessage;
import com.luvin.simulation.dto.ConversationMessageResponse;
import com.luvin.simulation.dto.ConversationSaveRequest;
import com.luvin.simulation.dto.MatchResponse;
import com.luvin.simulation.repository.EpisodeParticipantRepository;
import com.luvin.simulation.repository.EpisodeRepository;
import com.luvin.simulation.repository.MatchRepository;
import com.luvin.simulation.repository.OneOnOneMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final EpisodeRepository episodeRepository;
    private final MatchRepository matchRepository;
    private final EpisodeParticipantRepository episodeParticipantRepository;
    private final OneOnOneMessageRepository oneOnOneMessageRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MatchResponse> getMatches(Long memberId, Long episodeId) {
        episodeRepository.findByEpisodeIdAndMemberId(episodeId, memberId)
                .orElseThrow(() -> new EpisodeNotFoundException(episodeId));
        return matchRepository.findAllByEpisode_EpisodeId(episodeId).stream()
                .map(MatchResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MatchResponse getFinalCouple(Long memberId) {
        Match match = matchRepository.findFirstByEpisode_MemberIdAndIsFinalTrue(memberId)
                .orElseThrow(() -> new MatchNotFoundException("아직 최종 커플이 결정되지 않았습니다."));
        return MatchResponse.from(match);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> getOneOnOneConversation(Long memberId, Long matchId) {
        Match match = getMatchOrThrow(memberId, matchId);
        return oneOnOneMessageRepository.findAllByMatch_MatchIdOrderBySequenceAsc(match.getMatchId()).stream()
                .map(m -> new ConversationMessageResponse(
                        m.getSequence(), m.getSpeaker().getParticipantId(), m.getSpeaker().getName(), m.getContent()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveOneOnOneConversation(Long memberId, Long matchId, ConversationSaveRequest request) {
        Match match = getMatchOrThrow(memberId, matchId);
        for (ConversationSaveRequest.MessageItem item : request.getMessages()) {
            EpisodeParticipant speaker = episodeParticipantRepository
                    .findByParticipantIdAndEpisode_EpisodeId(item.getSpeakerParticipantId(), match.getEpisode().getEpisodeId())
                    .orElseThrow(() -> new ParticipantNotFoundException(item.getSpeakerParticipantId()));
            oneOnOneMessageRepository.save(
                    new OneOnOneMessage(match, speaker, item.getContent(), item.getSequence()));
        }
    }

    private Match getMatchOrThrow(Long memberId, Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        if (!match.getEpisode().getMemberId().equals(memberId)) {
            throw new MatchNotFoundException(matchId);
        }
        return match;
    }
}
