package com.luvin.ai.client.exception;

/**
 * 409 REVISION_CONFLICT. 최신 시즌을 다시 조회해서 사용자 동작이 여전히 유효한지 재판단해야 한다
 * (요구사항 6절). 무조건 자동 재시도하지 않는다.
 */
public class AiRevisionConflictException extends AiServiceException {
    public AiRevisionConflictException(String message, String aiErrorCode, String rawBody) {
        super(message, aiErrorCode, rawBody);
    }
}
