package com.luvin.ai.client.dto;

import java.math.BigDecimal;

/**
 * 13개 성향 점수. openapi.json의 Traits 스키마와 1:1 대응.
 * 모든 필드 필수, 1..100 범위. Spring이 AI 호출 전에 검증한다 (요구사항 3.3).
 *
 * BigDecimal인 이유: 설문 v2 채점은 소수 둘째 자리까지 의미 있는 점수(예 75.25)를 만든다.
 * AI 서비스가 실제로 decimal을 받는지 계약이 확인되기 전까지는
 * {@link com.luvin.ai.service.AiTraitsWireAdapter}가 wire 직전에 명시적으로 정수 반올림한다 —
 * 이 타입 자체는 정밀도를 그대로 유지해 향후 decimal 계약이 확정되면 절삭 없이 바로 전환할 수 있다.
 */
public record TraitsDto(
        BigDecimal affectionExpression,
        BigDecimal relationshipAnxiety,
        BigDecimal relationshipAvoidance,
        BigDecimal emotionalAttunement,
        BigDecimal relationshipInitiative,
        BigDecimal practicalPriority,
        BigDecimal reassuranceNeed,
        BigDecimal jealousyReactivity,
        BigDecimal relationshipEnergyDependence,
        BigDecimal emotionalSuppression,
        BigDecimal conflictConfrontation,
        BigDecimal relationshipPace,
        BigDecimal interestExpressionFrequency
) {
}
