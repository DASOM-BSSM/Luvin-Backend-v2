package com.luvin.ai.service.exception;

/**
 * AI 호출 전 Spring이 자체적으로 검증에 실패한 경우 (요구사항 3.3). 절대 AI로 요청을 보내지 않는다.
 */
public class AiInputValidationException extends RuntimeException {
    public AiInputValidationException(String message) {
        super(message);
    }
}
