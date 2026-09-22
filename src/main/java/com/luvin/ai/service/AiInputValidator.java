package com.luvin.ai.service;

import com.luvin.ai.dto.AiCharacterProfileRequest;
import com.luvin.ai.dto.AiTraitsRequest;
import com.luvin.ai.service.exception.AiInputValidationException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * AI 호출 전 Spring이 수행해야 하는 검증 (요구사항 3.3):
 * 성별 male|female, 나이 19..120, 성격 설명 1..1000자, 13개 성향 전부 필수·1..100·boolean/NaN/Infinity 금지.
 * 여기서 막힌 요청은 절대 AI 서비스로 전달되지 않는다.
 */
@Component
public class AiInputValidator {

    private static final Set<String> ALLOWED_GENDERS = Set.of("male", "female");
    private static final int MIN_ADULT_AGE = 19;
    private static final int MAX_ADULT_AGE = 120;
    private static final int MAX_PERSONALITY_LENGTH = 1000;
    private static final int TRAIT_MIN = 1;
    private static final int TRAIT_MAX = 100;

    public void validateCharacterProfile(AiCharacterProfileRequest profile) {
        if (profile == null) {
            throw new AiInputValidationException("캐릭터 프로필이 없습니다.");
        }
        validateGender(profile.gender());
        validateAdultAge(profile.adultAge());
        validatePersonality(profile.personality());
        validateTraits(profile.traits());
    }

    private void validateGender(String gender) {
        if (gender == null || !ALLOWED_GENDERS.contains(gender)) {
            throw new AiInputValidationException("gender는 male 또는 female이어야 합니다.");
        }
    }

    private void validateAdultAge(Integer adultAge) {
        if (adultAge == null || adultAge < MIN_ADULT_AGE || adultAge > MAX_ADULT_AGE) {
            throw new AiInputValidationException(
                    "adult_age는 " + MIN_ADULT_AGE + ".." + MAX_ADULT_AGE + " 범위의 정수여야 합니다.");
        }
    }

    private void validatePersonality(String personality) {
        if (personality == null || personality.isEmpty() || personality.length() > MAX_PERSONALITY_LENGTH) {
            throw new AiInputValidationException(
                    "personality는 1.." + MAX_PERSONALITY_LENGTH + "자여야 합니다.");
        }
    }

    private void validateTraits(AiTraitsRequest traits) {
        if (traits == null) {
            throw new AiInputValidationException("traits가 없습니다.");
        }

        Map<String, Integer> fields = new LinkedHashMap<>();
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

        for (Map.Entry<String, Integer> entry : fields.entrySet()) {
            Integer value = entry.getValue();
            if (value == null) {
                throw new AiInputValidationException("13개 성향 중 " + entry.getKey() + "가 누락되었습니다.");
            }
            // Integer 타입 자체가 boolean/NaN/Infinity를 표현할 수 없으므로 컴파일 타임에 그 경우를 배제한다.
            // 범위만 명시적으로 재검증한다.
            if (value < TRAIT_MIN || value > TRAIT_MAX) {
                throw new AiInputValidationException(
                        entry.getKey() + "는 " + TRAIT_MIN + ".." + TRAIT_MAX + " 범위여야 합니다. 입력값=" + value);
            }
        }

        if (fields.size() != 13) {
            throw new AiInputValidationException("13개 성향 필드가 모두 필요합니다.");
        }
    }
}
