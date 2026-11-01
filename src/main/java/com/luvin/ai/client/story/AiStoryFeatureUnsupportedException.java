package com.luvin.ai.client.story;

import com.luvin.ai.client.exception.AiServiceException;

/**
 * 이 AI 연결로는 쓸 수 없는 생성 방식(블록 단위 생성의 503 STREAMING_NOT_SUPPORTED, 동기 모드의 job 전용 task). 호출부는
 * 기존 방식으로 생성한다.
 */
public class AiStoryFeatureUnsupportedException extends AiServiceException {

    public static final String STREAMING_NOT_SUPPORTED = "STREAMING_NOT_SUPPORTED";

    public AiStoryFeatureUnsupportedException(String message) {
        super(message, STREAMING_NOT_SUPPORTED, null);
    }
}
