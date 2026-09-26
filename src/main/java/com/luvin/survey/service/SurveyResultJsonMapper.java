package com.luvin.survey.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.luvin.survey.definition.SurveyDefinitionDimension;
import com.luvin.survey.scoring.DimensionScore;
import com.luvin.survey.scoring.ObservedEvidence;
import com.luvin.survey.scoring.ReasonEvidence;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * survey_results의 JSONB 컬럼들을 Python classifier.py classify() 출력과 동일한 snake_case shape으로
 * (역)직렬화한다. 원본 답변은 survey_submission_answers에 남아있으므로 여기 저장하는 값은 표시/재현용이다.
 */
final class SurveyResultJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private SurveyResultJsonMapper() {
    }

    static String scoresToJson(Map<String, DimensionScore> scores, Map<String, SurveyDefinitionDimension> dimensions,
                                boolean core) {
        Map<String, DimensionScore> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, DimensionScore> entry : scores.entrySet()) {
            SurveyDefinitionDimension dimension = dimensions.get(entry.getKey());
            if (dimension != null && dimension.isCore() == core) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return writeValueAsString(filtered);
    }

    static String evidenceStatsToJson(Map<String, List<ObservedEvidence>> observedByDimension) {
        Map<String, Map<String, Integer>> stats = new LinkedHashMap<>();
        for (Map.Entry<String, List<ObservedEvidence>> entry : observedByDimension.entrySet()) {
            int weight = entry.getValue().stream().mapToInt(ObservedEvidence::weight).sum();
            int weightedSum = entry.getValue().stream().mapToInt(e -> e.weight() * e.value()).sum();
            Map<String, Integer> stat = new LinkedHashMap<>();
            stat.put("weighted_sum", weightedSum);
            stat.put("weight", weight);
            stats.put(entry.getKey(), stat);
        }
        return writeValueAsString(stats);
    }

    static String distancesToJson(Map<String, BigDecimal> distances) {
        return writeValueAsString(distances);
    }

    static String reasonEvidenceToJson(List<ReasonEvidence> reasons) {
        return writeValueAsString(reasons);
    }

    static List<ReasonEvidence> parseReasonEvidence(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<ReasonEvidence>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("저장된 explanation_evidence를 파싱할 수 없습니다.", e);
        }
    }

    private static String writeValueAsString(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("설문 결과를 JSON으로 직렬화할 수 없습니다.", e);
        }
    }
}
