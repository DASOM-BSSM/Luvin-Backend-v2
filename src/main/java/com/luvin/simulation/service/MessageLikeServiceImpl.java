package com.luvin.simulation.service;

import com.luvin.common.exception.MatchNotFoundException;
import com.luvin.common.exception.MessageNotFoundException;
import com.luvin.simulation.domain.Episode;
import com.luvin.simulation.domain.Match;
import com.luvin.simulation.domain.MessageLike;
import com.luvin.simulation.domain.OneOnOneMessage;
import com.luvin.simulation.dto.LikeMessageResponse;
import com.luvin.simulation.repository.MatchRepository;
import com.luvin.simulation.repository.MessageLikeRepository;
import com.luvin.simulation.repository.OneOnOneMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageLikeServiceImpl implements MessageLikeService {

    private final MatchRepository matchRepository;
    private final OneOnOneMessageRepository oneOnOneMessageRepository;
    private final MessageLikeRepository messageLikeRepository;

    @Override
    @Transactional
    public void likeMessage(Long memberId, Long matchId, Long messageId) {
        Match match = getOwnedMatchOrThrow(memberId, matchId);
        OneOnOneMessage message = oneOnOneMessageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));
        if (!message.getMatch().getMatchId().equals(matchId)) {
            // 다른 매칭의 메시지를 이 matchId로 요청한 경우: 존재 여부를 그대로 흘리지 않고
            // MatchNotFoundException과 동일한 정보 은닉 원칙으로 404 처리한다.
            throw new MessageNotFoundException(messageId);
        }

        Episode episode = match.getEpisode();
        messageLikeRepository.findByMemberIdAndEpisode_EpisodeId(memberId, episode.getEpisodeId())
                .ifPresentOrElse(
                        existing -> {
                            if (!existing.getMessage().getMessageId().equals(messageId)) {
                                existing.changeMessage(message);
                            }
                        },
                        () -> messageLikeRepository.save(new MessageLike(memberId, episode, message))
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<LikeMessageResponse> getLikedMessages(Long memberId) {
        return messageLikeRepository.findAllByMemberIdOrderByEpisode_EpisodeNumberAsc(memberId).stream()
                .map(like -> new LikeMessageResponse(
                        like.getEpisode().getEpisodeNumber(),
                        like.getMessage().getMessageId(),
                        like.getMessage().getContent()))
                .collect(Collectors.toList());
    }

    private Match getOwnedMatchOrThrow(Long memberId, Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        if (!match.getEpisode().getMemberId().equals(memberId)) {
            throw new MatchNotFoundException(matchId);
        }
        return match;
    }
}
