package com.luvin.ai.client.dto;

/**
 * 13개 성향 점수. openapi.json의 Traits 스키마와 1:1 대응.
 * 모든 필드 필수, 1..100 범위. Spring이 AI 호출 전에 검증한다 (요구사항 3.3).
 */
public record TraitsDto(
        Integer affectionExpression,
        Integer relationshipAnxiety,
        Integer relationshipAvoidance,
        Integer emotionalAttunement,
        Integer relationshipInitiative,
        Integer practicalPriority,
        Integer reassuranceNeed,
        Integer jealousyReactivity,
        Integer relationshipEnergyDependence,
        Integer emotionalSuppression,
        Integer conflictConfrontation,
        Integer relationshipPace,
        Integer interestExpressionFrequency
) {
}
