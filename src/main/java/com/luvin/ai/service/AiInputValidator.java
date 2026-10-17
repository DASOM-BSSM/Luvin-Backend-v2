package com.luvin.ai.service;

import com.luvin.ai.dto.AiCharacterProfileRequest;
import com.luvin.ai.dto.AiTraitsRequest;
import com.luvin.ai.service.exception.AiInputValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * AI 호출 전 Spring이 수행해야 하는 검증 (요구사항 3.3):
 * 성별 male|female, 13개 성향 전부 필수·1..100·boolean/NaN/Infinity 금지.
 * 여기서 막힌 요청은 절대 AI 서비스로 전달되지 않는다.
 *
 * BigDecimal 자체가 boolean/NaN/Infinity를 표현할 수 없으므로(파싱 시점에 이미 배제됨),
 * 여기서는 null 여부와 1..100 범위만 compareTo로 재검증한다. 문자열 숫자("50")가 JSON 바인딩
 * 단계에서 자동 강제 변환되는지는 컨트롤러의 Jackson 설정에 달려 있고 이 클래스 범위 밖이다.
 */
@Component
public class AiInputValidator {

    private static final Set<String> ALLOWED_GENDERS = Set.of("male", "female");
    private static final BigDecimal TRAIT_MIN = BigDecimal.ONE;
    private static final BigDecimal TRAIT_MAX = BigDecimal.valueOf(100);

    public void validateCharacterProfile(AiCharacterProfileRequest profile) {
        if (profile == null) {
            throw new AiInputValidationException("캐릭터 프로필이 없습니다.");
        }
        validateGender(profile.gender());
        validateTraits(profile.traits());
    }

    private void validateGender(String gender) {
        if (gender == null || !ALLOWED_GENDERS.contains(gender)) {
            throw new AiInputValidationException("gender는 male 또는 female이어야 합니다.");
        }
    }

    private void validateTraits(AiTraitsRequest traits) {
        if (traits == null) {
            throw new AiInputValidationException("traits가 없습니다.");
        }

        Map<String, BigDecimal> fields = new LinkedHashMap<>();
        fields.put("affection_expression", traits.affectionExpression());
        fields.put("relationship_anxiety", traits.relationshipAnxiety());
        fields.put("relationship_avoidance", traits.relationshipAvoidance());
        fields.put("emotional_attunement", traits.emotionalAttunement());
        fields.put("relationship_initiative", traits.relationshipInitiative());
        fields.put("practical_priority", traits.practicalPriority());
        fields.put("reassurance_need", traits.reassuranceNeed());
        fields.put("jealousy_reactivity", traits.jealousyReactivity());
        fields.put("relationship_energy_dependence", traits.relationshipEnergyDependence());
        fields.put("emotional_suppression", traits.emotionalSuppression());
        fields.put("conflict_confrontation", traits.conflictConfrontation());
        fields.put("relationship_pace", traits.relationshipPace());
        fields.put("interest_expression_frequency", traits.interestExpressionFrequency());

        for (Map.Entry<String, BigDecimal> entry : fields.entrySet()) {
            BigDecimal value = entry.getValue();
            if (value == null) {
                throw new AiInputValidationException("13개 성향 중 " + entry.getKey() + "가 누락되었습니다.");
            }
            if (value.compareTo(TRAIT_MIN) < 0 || value.compareTo(TRAIT_MAX) > 0) {
                throw new AiInputValidationException(
                        entry.getKey() + "는 " + TRAIT_MIN + ".." + TRAIT_MAX + " 범위여야 합니다. 입력값=" + value);
            }
        }

        if (fields.size() != 13) {
            throw new AiInputValidationException("13개 성향 필드가 모두 필요합니다.");
        }
    }
}
