package com.luvin.ai.client.exception;

/**
 * 5xx 또는 네트워크 timeout. 기존 job/idempotency key를 확인한 뒤 제한적으로 재시도할 수 있다
 * (요구사항 6절, 5.3).
 */
public class AiServerException extends AiServiceException {
    public AiServerException(String message, String rawBody) {
        super(message, null, rawBody);
    }

    public AiServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
