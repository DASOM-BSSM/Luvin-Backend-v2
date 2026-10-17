package com.luvin.ai.dto;

import java.math.BigDecimal;

public record AiTraitsRequest(
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
