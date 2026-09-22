package com.luvin.ai.client.dto;

import java.util.UUID;

/**
 * status는 openapi 상 자유 문자열이라 원문 그대로 받고, 알려진 값인지는
 * {@link com.luvin.ai.domain.AiJobStatus#fromWire(String)}에서 검증한다.
 * 알 수 없는 status를 성공으로 처리하지 않기 위함 (요구사항 3.1).
 */
public record JobResponseDto(
        UUID jobId,
        String status,
        String statusUrl,
        UUID resultVersionId,
        String errorCode
) {
}
