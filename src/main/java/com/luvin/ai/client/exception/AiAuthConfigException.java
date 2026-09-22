package com.luvin.ai.client.exception;

/**
 * 401. Spring 서버 설정(예: 향후 service JWT) 문제로 간주한다.
 * 절대 "사용자 재로그인 필요" 오류로 변환하지 않는다 (요구사항 6절).
 */
public class AiAuthConfigException extends AiServiceException {
    public AiAuthConfigException(String message, String rawBody) {
        super(message, null, rawBody);
    }
}
