package com.luvin.ai.client.exception;

/**
 * 알 수 없는 enum/status 등 openapi 계약을 벗어난 응답을 받았을 때 발생시킨다.
 * 이런 응답을 성공으로 처리하지 않고 관측 가능한 오류로 전환하기 위함이다 (요구사항 3.1).
 */
public class AiContractViolationException extends AiServiceException {
    public AiContractViolationException(String message, String rawBody) {
        super(message, null, rawBody);
    }
}
