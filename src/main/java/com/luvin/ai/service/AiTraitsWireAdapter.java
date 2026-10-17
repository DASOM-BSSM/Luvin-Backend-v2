package com.luvin.ai.service;

import com.luvin.ai.client.dto.TraitsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

/**
 * AI 서비스가 실제로 decimal 성향 점수를 받는지는 이 저장소만으로 확정할 수 없다
 * (BACKEND_CHANGES.md 9절). 확인 전까지는 무음으로 소수를 그대로 보내거나 무음으로 잘라내지 않고,
 * 이 어댑터가 "정수로 반올림해서 보낸다"는 결정을 명시적으로 표현한다.
 *
 * app.ai.decimal-traits-enabled=true로 바뀌면(AI 쪽 decimal 계약이 확인된 뒤) 반올림 없이 그대로
 * 보낸다. 기본값 false는 과거부터 AI가 항상 정수만 받아온 이력과 같은 보수적 동작이다.
 */
@Component
public class AiTraitsWireAdapter {

    private final boolean decimalTraitsEnabled;

    public AiTraitsWireAdapter(@Value("${app.ai.decimal-traits-enabled:false}") boolean decimalTraitsEnabled) {
        this.decimalTraitsEnabled = decimalTraitsEnabled;
    }

    public TraitsDto forWire(TraitsDto traits) {
        if (decimalTraitsEnabled) {
            return traits;
        }
        return new TraitsDto(
                round(traits.affectionExpression()), round(traits.relationshipAnxiety()),
                round(traits.relationshipAvoidance()), round(traits.emotionalAttunement()),
                round(traits.relationshipInitiative()), round(traits.practicalPriority()),
                round(traits.reassuranceNeed()), round(traits.jealousyReactivity()),
                round(traits.relationshipEnergyDependence()), round(traits.emotionalSuppression()),
                round(traits.conflictConfrontation()), round(traits.relationshipPace()),
                round(traits.interestExpressionFrequency()));
    }

    private java.math.BigDecimal round(java.math.BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
