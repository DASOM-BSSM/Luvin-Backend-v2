package com.luvin.ai.service.exception;

/**
 * 시즌이 없거나 다른 사용자 소유. 다른 사용자 자원의 존재 여부를 노출하지 않기 위해 항상
 * 동일한 일반 404 메시지로 처리한다 (요구사항 6절, 7절).
 */
public class AiSeasonNotFoundException extends RuntimeException {
    public AiSeasonNotFoundException() {
        super("시즌을 찾을 수 없습니다.");
    }
}
