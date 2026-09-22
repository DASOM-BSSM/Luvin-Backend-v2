package com.luvin.ai.domain;

import com.luvin.ai.client.exception.AiContractViolationException;

/**
 * GET /v1/jobs/{job_id} status. 요구사항 5.3의 상태 처리표와 1:1 대응.
 * 알 수 없는 문자열은 절대 성공으로 처리하지 않고 계약 위반 예외로 전환한다 (요구사항 3.1).
 */
public enum AiJobStatus {
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED;

    public static AiJobStatus fromWire(String wireValue, String rawBody) {
        if (wireValue == null) {
            throw new AiContractViolationException("job status가 null입니다.", rawBody);
        }
        try {
            return AiJobStatus.valueOf(wireValue.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AiContractViolationException("알 수 없는 job status: " + wireValue, rawBody);
        }
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }

    public boolean isPending() {
        return this == QUEUED || this == RUNNING || this == RETRY_WAIT;
    }
}
