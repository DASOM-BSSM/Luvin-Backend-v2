package com.luvin.ai.client.exception;

/**
 * 409 REROLL_LOCKED. 다음 화 generation이 이미 접수되어 reroll이 잠긴, 정상적인 도메인 충돌이다
 * (요구사항 5.5). 예외적 오류가 아니라 앱에 그대로 안내할 상태로 처리한다.
 */
public class AiRerollLockedException extends AiServiceException {
    public AiRerollLockedException(String message, String rawBody) {
        super(message, "REROLL_LOCKED", rawBody);
    }
}
