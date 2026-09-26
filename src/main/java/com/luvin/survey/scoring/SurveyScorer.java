package com.luvin.survey.scoring;

import com.luvin.survey.definition.SurveyDefinitionAnswer;
import com.luvin.survey.definition.SurveyDefinitionConfig;
import com.luvin.survey.definition.SurveyDefinitionEvidence;
import com.luvin.survey.definition.SurveyDefinitionQuestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Python classifier.py classify()의 채점 절반(지표별 score/evidence 집계)을 Java로 이식한 순수 계산기.
 * 어떤 단계에서도 소수 반올림을 하지 않고, {@link Rational}로 정확한 유리수 연산을 유지한다.
 */
public final class SurveyScorer {

    private static final Rational CENTER = Rational.of(101, 2);
    private static final Rational HALF_RANGE = Rational.of(99, 2);

    private SurveyScorer() {
    }

    public static SurveyScoringResult score(SurveyDefinitionConfig config, String requestSurveyVersion,
                                             Map<String, String> answers) {
        if (!config.surveyVersion().equals(requestSurveyVersion)) {
            throw new SurveyAnswerValidationException("SURVEY_VERSION_MISMATCH",
                    "survey_version이 현재 definition과 일치하지 않습니다.");
        }
        if (answers == null) {
            throw new SurveyAnswerValidationException("INVALID_ANSWERS", "answers가 없습니다.");
        }
        Set<String> expectedQuestionIds = config.questions().stream()
                .map(SurveyDefinitionQuestion::id).collect(Collectors.toSet());
        if (!answers.keySet().equals(expectedQuestionIds)) {
            throw new SurveyAnswerValidationException("MISSING_OR_UNKNOWN_QUESTION",
                    "20개 문항 집합과 제출한 답변의 질문 집합이 일치하지 않습니다.");
        }

        Map<String, List<ObservedEvidence>> observed = new LinkedHashMap<>();
        for (String dimensionKey : config.dimensions().keySet()) {
            observed.put(dimensionKey, new ArrayList<>());
        }

        for (SurveyDefinitionQuestion question : config.questions()) {
            String answerId = answers.get(question.id());
            SurveyDefinitionAnswer answer = question.answerById(answerId);
            if (answer == null) {
                throw new SurveyAnswerValidationException("INVALID_ANSWER",
                        "질문 " + question.id() + "의 answerId가 유효하지 않습니다: " + answerId);
            }
            for (Map.Entry<String, SurveyDefinitionEvidence> entry : answer.evidence().entrySet()) {
                observed.get(entry.getKey()).add(new ObservedEvidence(
                        question.id(), answer.id(), entry.getValue().value(), entry.getValue().weight()));
            }
        }

        Map<String, Rational> exactScores = new LinkedHashMap<>();
        Map<String, DimensionScore> scores = new LinkedHashMap<>();
        for (Map.Entry<String, List<ObservedEvidence>> entry : observed.entrySet()) {
            List<ObservedEvidence> evidenceList = entry.getValue();
            int weight = evidenceList.stream().mapToInt(ObservedEvidence::weight).sum();
            long weightedSum = evidenceList.stream().mapToLong(e -> (long) e.weight() * e.value()).sum();
            if (weight == 0) {
                throw new SurveyAnswerValidationException("UNOBSERVED_DIMENSION",
                        "지표 " + entry.getKey() + "에 대한 근거가 없습니다.");
            }
            Rational exactScore = CENTER.add(HALF_RANGE.multiply(
                    Rational.of(weightedSum, config.priorWeight() + weight)));
            exactScores.put(entry.getKey(), exactScore);

            boolean hasPositive = evidenceList.stream().anyMatch(e -> e.value() == 1);
            boolean hasNegative = evidenceList.stream().anyMatch(e -> e.value() == -1);
            scores.put(entry.getKey(), new DimensionScore(
                    exactScore.toBigDecimal(2), weight, evidenceList.size(), hasPositive && hasNegative));
        }

        return new SurveyScoringResult(exactScores, scores, observed);
    }
}
