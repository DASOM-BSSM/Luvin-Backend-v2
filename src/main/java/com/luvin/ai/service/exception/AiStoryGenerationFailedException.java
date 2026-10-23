package com.luvin.ai.service.exception;

/**
 * FastAPI 생성 응답이 1회 재시도 후에도 실패(오류 응답 또는 형식 검증 실패)했다. 이미 저장된 회차에는 영향이 없다.
 * 앱에는 502 STORY_GENERATION_FAILED로 알리고, 같은 요청을 다시 보내면 처음부터 다시 시도한다.
 */
public class AiStoryGenerationFailedException extends RuntimeException {

    public static final String CODE = "STORY_GENERATION_FAILED";

    public AiStoryGenerationFailedException(String operation, Throwable cause) {
        super("이야기를 만들지 못했어요. 잠시 후 다시 시도해주세요. (" + operation + ")", cause);
    }
}
