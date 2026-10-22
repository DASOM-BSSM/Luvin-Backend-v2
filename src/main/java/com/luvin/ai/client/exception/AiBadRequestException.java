package com.luvin.ai.client.exception;

/**
 * 400. Spring이 계약에 맞지 않는 요청을 보낸 경우다. job SSE에서는 앱이 넘긴 Last-Event-ID가
 * 잘못된 cursor일 때 온다. 다른 endpoint에서는 기존처럼 AiServiceException(502)으로 처리된다.
 */
public class AiBadRequestException extends AiServiceException {
    public AiBadRequestException(String message, String aiErrorCode, String rawBody) {
        super(message, aiErrorCode, rawBody);
    }
}
