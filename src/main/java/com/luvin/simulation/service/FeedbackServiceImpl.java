package com.luvin.simulation.service;

import com.luvin.common.exception.EpisodeNotFoundException;
import com.luvin.common.exception.InvalidFeedbackException;
import com.luvin.common.exception.MessageNotFoundException;
import com.luvin.simulation.domain.ActionFeedback;
import com.luvin.simulation.domain.Episode;
import com.luvin.simulation.domain.FeedbackType;
import com.luvin.simulation.domain.OneOnOneMessage;
import com.luvin.simulation.dto.FeedbackRequest;
import com.luvin.simulation.dto.FeedbackResponse;
import com.luvin.simulation.repository.ActionFeedbackRepository;
import com.luvin.simulation.repository.EpisodeRepository;
import com.luvin.simulation.repository.OneOnOneMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final EpisodeRepository episodeRepository;
    private final OneOnOneMessageRepository oneOnOneMessageRepository;
    private final ActionFeedbackRepository actionFeedbackRepository;

    @Override
    @Transactional
    public FeedbackResponse saveFeedback(Long memberId, Long episodeId, FeedbackRequest request) {
        Episode episode = episodeRepository.findByEpisodeIdAndMemberId(episodeId, memberId)
                .orElseThrow(() -> new EpisodeNotFoundException(episodeId));

        Long messageId = request.getMessageId();
        OneOnOneMessage message = oneOnOneMessageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));
        if (!message.getMatch().getEpisode().getEpisodeId().equals(episode.getEpisodeId())) {
            // 다른 에피소드의 메시지: 존재 여부를 흘리지 않도록 없는 메시지와 똑같이 404 처리한다.
            throw new MessageNotFoundException(messageId);
        }
        if (!message.getSpeaker().isSelf()) {
            throw new InvalidFeedbackException("내 AI 분신이 한 말에만 피드백할 수 있습니다.");
        }

        FeedbackType feedbackType = parseFeedbackType(request.getFeedbackType());
        ActionFeedback feedback = actionFeedbackRepository.findByMemberIdAndMessage_MessageId(memberId, messageId)
                .map(existing -> {
                    existing.update(feedbackType, request.getContent());
                    return existing;
                })
                .orElseGet(() -> actionFeedbackRepository.save(
                        new ActionFeedback(memberId, episode, message, feedbackType, request.getContent())));

        return new FeedbackResponse(feedback.getFeedbackId(), messageId, "피드백이 저장되었습니다.");
    }

    private FeedbackType parseFeedbackType(String value) {
        return Arrays.stream(FeedbackType.values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new InvalidFeedbackException(
                        "feedbackType은 " + Arrays.toString(FeedbackType.values()) + " 중 하나여야 합니다."));
    }
}
