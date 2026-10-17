package com.luvin.ai.dto;

import java.util.UUID;

/**
 * surveyResultId가 없으면 사용자의 현재 최신 설문 결과를 사용한다.
 * clientRequestId는 선택이며, 같은 값으로 재요청하면 토큰을 다시 차감하지 않고 같은 시즌을 반환한다
 * (버튼 연타/네트워크 재시도 대비 — 주지 않아도 "다음 시즌 번호" 자체가 자연스러운 idempotency 키가 된다).
 */
public record StartNextSeasonRequest(UUID surveyResultId, String clientRequestId) {
}
