package com.luvin.ai.dto.story;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * 장면 블록 하나. type에 따라 쓰는 필드만 응답에 담긴다(나머지는 빠진다).
 * - group / pair: participants, messages
 * - transition: transitionText
 * - messenger: messenger, participants, messages
 * - choice: choice ("혼자 있기"는 프론트가 붙이므로 candidates에 없다)
 * - choice_result: choiceResult, participants, messages
 * - interview: interview, participants, messages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiSceneBlockView(
        String id,
        String type,
        List<AiStoryParticipantView> participants,
        List<AiStoryMessageView> messages,
        String transitionText,
        SceneMessenger messenger,
        SceneChoice choice,
        SceneChoiceResult choiceResult,
        SceneInterview interview
) {

    /** kind: direct | group */
    public record SceneMessenger(String kind, String roomName) {
    }

    public record SceneChoice(int pointId, String question, List<SceneChoiceCandidate> candidates) {
    }

    /** memory는 관계 기록에서 뽑은 "기억 한 줄". */
    public record SceneChoiceCandidate(UUID breadId, String memory) {
    }

    /** breadId가 null이면 혼자 있기를 고른 결과다. */
    public record SceneChoiceResult(int pointId, UUID breadId, boolean accepted) {
    }

    public record SceneInterview(UUID breadId) {
    }
}
