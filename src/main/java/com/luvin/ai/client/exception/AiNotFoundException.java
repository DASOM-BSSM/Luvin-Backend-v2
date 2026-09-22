package com.luvin.ai.client.exception;

/**
 * 404. owner 불일치 또는 자원 없음. 다른 사용자 자원의 존재 여부를 노출하지 않기 위해
 * Spring API 응답에서는 일반적인 404로만 표현한다 (요구사항 6절).
 */
public class AiNotFoundException extends AiServiceException {
    public AiNotFoundException(String message, String rawBody) {
        super(message, null, rawBody);
    }
}
