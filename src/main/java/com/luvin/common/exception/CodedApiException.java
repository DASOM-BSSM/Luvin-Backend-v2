package com.luvin.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 앱이 원인별로 화면을 바꿀 수 있게 HTTP 상태와 machine code(ApiResponse.code)를 함께 내려주는 오류.
 * data는 선택이다(예: 409 ROOM_QUESTION_ALREADY_EXISTS면 이미 있는 질문).
 */
@Getter
public class CodedApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Object data;

    public CodedApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public CodedApiException(HttpStatus status, String code, String message, Object data) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }
}
