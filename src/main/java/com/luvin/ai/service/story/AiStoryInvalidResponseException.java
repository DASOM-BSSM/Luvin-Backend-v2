package com.luvin.ai.service.story;

/** FastAPI 생성 응답이 Spring 쪽 형식 검증을 통과하지 못했다. 호출부가 1회 재시도한다. */
public class AiStoryInvalidResponseException extends RuntimeException {
    public AiStoryInvalidResponseException(String message) {
        super(message);
    }
}
