package com.luvin.ai.client.exception;

/**
 * AI 서비스 연동 과정에서 발생하는 모든 예외의 기반 클래스.
 * 요구사항 6절의 HTTP/status별 Spring 처리 분류를 그대로 따르는 하위 타입으로 세분화한다.
 */
public class AiServiceException extends RuntimeException {

    private final String aiErrorCode;
    private final String rawBody;

    public AiServiceException(String message, String aiErrorCode, String rawBody) {
        super(message);
        this.aiErrorCode = aiErrorCode;
        this.rawBody = rawBody;
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
        this.aiErrorCode = null;
        this.rawBody = null;
    }

    /** AI 서비스가 내려준 error code (있다면). 사용자에게 그대로 노출하지 않는다 (요구사항 7절). */
    public String getAiErrorCode() {
        return aiErrorCode;
    }

    /** 원본 응답 body. 로그에도 provider 원문을 그대로 남기지 않도록 주의해서 다룬다 (요구사항 7절). */
    public String getRawBody() {
        return rawBody;
    }
}
