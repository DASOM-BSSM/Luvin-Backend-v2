package com.luvin.ai.client.exception;

/**
 * 429. 제한된 횟수로 backoff 후 재시도하고, 사용자에게는 처리 지연 상태로 안내한다 (요구사항 6절).
 */
public class AiRateLimitException extends AiServiceException {
    public AiRateLimitException(String message, String rawBody) {
        super(message, null, rawBody);
    }
}
