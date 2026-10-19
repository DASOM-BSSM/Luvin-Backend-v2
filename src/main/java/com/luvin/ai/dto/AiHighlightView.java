package com.luvin.ai.dto;

/**
 * 5화 리포트 하이라이트 하나(2화 또는 4화). 항상 사용자 대변 AI(representative)의 발화만 담는다
 * (2025 스키마 변경, PR #5) — 발화자 식별자(messageId/speakerId/speakerRole)와 원문
 * 그대로(text)/observedBehavior는 더 이상 오지 않고 message/reason 두 개만 남았다.
 */
public record AiHighlightView(
        String message,
        String reason
) {
}
