package com.luvin.ai.client.exception;

/**
 * 409 LETTER_ALREADY_EXISTS. 오류가 아니라 "이미 정상 생성된 상태"라는 뜻이므로, 호출부는 이
 * 예외를 받으면 GET /letter로 받아와 그대로 성공 처리해야 한다.
 */
public class AiLetterAlreadyExistsException extends AiServiceException {
    public AiLetterAlreadyExistsException(String message, String rawBody) {
        super(message, "LETTER_ALREADY_EXISTS", rawBody);
    }
}
