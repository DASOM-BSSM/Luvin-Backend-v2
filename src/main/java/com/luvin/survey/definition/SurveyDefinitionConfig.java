package com.luvin.survey.definition;

import java.util.List;
import java.util.Map;

/**
 * 설문 definition 하나(불변). surveyVersion/scoringVersion/classificationVersion이 함께 하나의
 * definition을 고정한다 — 문항/가중치/유형 프로필 중 무엇이 바뀌어도 새 definition을 발급해야 한다.
 */
public record SurveyDefinitionConfig(
        String surveyVersion,
        String scoringVersion,
        String classificationVersion,
        String status,
        int priorWeight,
        int distanceGapThreshold,
        int poorFitDistanceThreshold,
        Map<String, SurveyDefinitionDimension> dimensions,
        List<SurveyDefinitionQuestion> questions,
        List<SurveyDefinitionProfile> profiles,
        List<String> tieBreakOrder
) {
    public SurveyDefinitionQuestion questionById(String questionId) {
        return questions.stream().filter(q -> q.id().equals(questionId)).findFirst().orElse(null);
    }
}
