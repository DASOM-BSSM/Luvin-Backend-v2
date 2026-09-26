package com.luvin.survey.scoring;

import java.util.List;
import java.util.Map;

/**
 * {@link SurveyScorer}의 계산 결과. exactScores는 반올림하지 않은 정확한 유리수 점수로,
 * {@link BreadTypeClassifier}가 거리 계산에 그대로 사용한다(표시용 score를 재사용하면 정밀도를 잃는다).
 */
public record SurveyScoringResult(
        Map<String, Rational> exactScores,
        Map<String, DimensionScore> scores,
        Map<String, List<ObservedEvidence>> observedByDimension
) {
}
