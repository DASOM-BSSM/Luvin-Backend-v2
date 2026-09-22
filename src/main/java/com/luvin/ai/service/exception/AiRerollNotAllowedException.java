package com.luvin.ai.service.exception;

/**
 * 다음 화 generation이 이미 접수되어 reroll이 로컬 상태 기준으로 잠긴 경우.
 * AI 서비스가 내려주는 REROLL_LOCKED(AiRerollLockedException)와 별개로, 왕복을 줄이기 위해
 * Spring이 먼저 알 수 있는 경우 이 예외로 빠르게 막는다 (요구사항 5.5).
 */
public class AiRerollNotAllowedException extends RuntimeException {
    public AiRerollNotAllowedException(String message) {
        super(message);
    }
}
