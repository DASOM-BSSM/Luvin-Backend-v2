package com.luvin.ai.client.exception;

/**
 * 409 계열 중 selection 진행 순서/중복 충돌 (예: 이미 1화 선택이 존재).
 */
public class AiSelectionConflictException extends AiServiceException {
    public AiSelectionConflictException(String message, String aiErrorCode, String rawBody) {
        super(message, aiErrorCode, rawBody);
    }
}
