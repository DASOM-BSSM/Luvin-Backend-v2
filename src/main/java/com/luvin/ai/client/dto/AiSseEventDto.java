package com.luvin.ai.client.dto;

/**
 * GET /v1/jobs/{job_id}/events로 받은 SSE 이벤트 1개. data는 AI가 보낸 원문 그대로다
 * (delta의 text는 검증 전 JSON 조각이라 Spring은 해석하지 않고 앱으로 그대로 넘긴다).
 * id는 재연결용 Last-Event-ID 값이고, AI가 id를 안 붙인 이벤트면 null이다.
 */
public record AiSseEventDto(
        String id,
        String event,
        String data
) {
}
