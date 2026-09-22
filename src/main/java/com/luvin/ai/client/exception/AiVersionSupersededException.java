package com.luvin.ai.client.exception;

/**
 * 409 VERSION_SUPERSEDED. 조회 중이던 version이 reroll 등으로 교체됨.
 * 서로 다른 version의 페이지를 합치지 않고 새 active version의 첫 페이지부터 다시 읽어야 한다 (요구사항 5.6).
 */
public class AiVersionSupersededException extends AiServiceException {
    public AiVersionSupersededException(String message, String rawBody) {
        super(message, "VERSION_SUPERSEDED", rawBody);
    }
}
