package com.luvin.ai.service.exception;

/**
 * 화 진행 순서 위반 (예: 2화 완료 전 3화 generation 요청, generation 전 메시지 조회 등).
 * 정상적인 도메인 충돌로 취급한다 (요구사항 6절 - 409 계열).
 */
public class AiEpisodeProgressionException extends RuntimeException {
    public AiEpisodeProgressionException(String message) {
        super(message);
    }
}
