package com.luvin.ai.service.exception;

import lombok.Getter;

/**
 * AI 호출 전 Spring이 자체적으로 검증에 실패한 경우 (요구사항 3.3). 절대 AI로 요청을 보내지 않는다.
 * code가 있으면 400 응답의 code로 내려간다(앱이 원인별 안내 문구를 고를 수 있게). 없으면 null.
 */
@Getter
public class AiInputValidationException extends RuntimeException {

    /** 1:1 장면 피드백·좋아요(scene-feedback, scene-likes) 400 code. */
    public static final String SCENE_ORDER_MISMATCH = "SCENE_ORDER_MISMATCH";
    public static final String SCENE_BLOCK_NOT_FOUND = "SCENE_BLOCK_NOT_FOUND";
    public static final String SCENE_NOT_PAIR = "SCENE_NOT_PAIR";
    public static final String SCENE_MESSAGE_NOT_FOUND = "SCENE_MESSAGE_NOT_FOUND";
    public static final String SCENE_NOT_MY_LINE = "SCENE_NOT_MY_LINE";
    public static final String SCENE_MONOLOGUE = "SCENE_MONOLOGUE";
    public static final String FEEDBACK_TOO_LONG = "FEEDBACK_TOO_LONG";

    private final String code;

    public AiInputValidationException(String message) {
        this(null, message);
    }

    public AiInputValidationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
