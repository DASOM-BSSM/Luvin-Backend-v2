package com.luvin.ai.service.exception;

import lombok.Getter;

/**
 * 장면 블록 회차 진행 순서·전제 조건 위반. 앱이 원인별로 화면을 바꿀 수 있게 409와 함께 code를 내려준다.
 * 예: AGE_REQUIRED(나이 입력 바텀시트), PREVIOUS_EPISODE_NOT_COMPLETED, CHOICE_REQUIRED.
 */
@Getter
public class AiStoryConflictException extends RuntimeException {

    public static final String AGE_REQUIRED = "AGE_REQUIRED";
    public static final String STORY_NOT_STARTED = "STORY_NOT_STARTED";
    public static final String PREVIOUS_EPISODE_NOT_COMPLETED = "PREVIOUS_EPISODE_NOT_COMPLETED";
    public static final String EPISODE_NOT_RELEASED = "EPISODE_NOT_RELEASED";
    public static final String CONTENT_NOT_READY = "CONTENT_NOT_READY";
    public static final String CHOICE_REQUIRED = "CHOICE_REQUIRED";
    public static final String CHOICE_ALREADY_MADE = "CHOICE_ALREADY_MADE";
    /** 선택은 했지만 결과 장면을 아직 만드는 중이거나 실패했다(POST complete). */
    public static final String CHOICE_RESULT_NOT_READY = "CHOICE_RESULT_NOT_READY";
    public static final String EPISODE_ALREADY_COMPLETED = "EPISODE_ALREADY_COMPLETED";
    public static final String REPORT_NOT_READY = "REPORT_NOT_READY";
    /** 열지 않은 4~10화의 content를 요청했다 — 앱이 회차 열기 화면을 띄운다. */
    public static final String EPISODE_NOT_UNLOCKED = "EPISODE_NOT_UNLOCKED";

    private final String code;

    public AiStoryConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
}
