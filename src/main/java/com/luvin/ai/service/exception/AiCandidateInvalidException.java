package com.luvin.ai.service.exception;

/**
 * partner_id가 현재 시즌의 candidate 목록에 없을 때 (요구사항 3.3). 클라이언트가 보낸 season/partner ID를
 * 검증 없이 신뢰하지 않기 위한 방어 로직이다 (요구사항 7절).
 */
public class AiCandidateInvalidException extends RuntimeException {
    public AiCandidateInvalidException(String message) {
        super(message);
    }
}
