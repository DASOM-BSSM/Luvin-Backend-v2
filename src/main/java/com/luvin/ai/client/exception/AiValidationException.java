package com.luvin.ai.client.exception;

/**
 * 422. Spring이 요청 DTO/범위를 잘못 구성했다는 뜻이다. Spring이 사전 검증을 했음에도 발생했다면
 * 계약 불일치이므로 자동 재시도하지 않고 버그로 취급한다 (요구사항 6절).
 */
public class AiValidationException extends AiServiceException {
    public AiValidationException(String message, String rawBody) {
        super(message, null, rawBody);
    }
}
