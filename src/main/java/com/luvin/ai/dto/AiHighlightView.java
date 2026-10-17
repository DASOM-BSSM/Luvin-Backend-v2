package com.luvin.ai.dto;

import com.luvin.ai.domain.AiCharacterRole;

import java.util.UUID;

/**
 * 5화 리포트의 하이라이트 메시지 하나. 실제 생성 대화에서 뽑힌 메시지의 발화자(speakerId/speakerRole),
 * 원문(text), 선정 이유(reason), 그 메시지에서 드러난 점(observedBehavior)을 담는다.
 * 화면에서 발화 AI 이름을 표시할 때 speakerId와 speakerRole을 함께 사용한다.
 */
public record AiHighlightView(
        UUID messageId,
        UUID speakerId,
        AiCharacterRole speakerRole,
        String text,
        String reason,
        String observedBehavior
) {
}
