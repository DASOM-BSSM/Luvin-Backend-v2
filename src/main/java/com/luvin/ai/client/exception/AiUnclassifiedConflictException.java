package com.luvin.ai.client.exception;

/**
 * AI 서비스가 409를 반환했지만 error_code가 없어(FastAPI detail 자유 텍스트 등) 구체적인
 * 충돌 유형을 특정할 수 없을 때. HTTP status(409)만으로도 "revision, 진행 순서, selection,
 * reroll 또는 version 충돌"(SPRING_BACKEND_INTEGRATION.md 8절)이라는 건 확실하므로,
 * 일반 500계열 오류로 흘려보내지 않고 이 전용 타입으로 구분해 재시도 대상으로 삼는다.
 */
public class AiUnclassifiedConflictException extends AiServiceException {
    public AiUnclassifiedConflictException(String message, String rawBody) {
        super(message, null, rawBody);
    }
}
