package com.luvin.survey.scoring;

import com.luvin.survey.definition.SurveyDefinitionConfig;
import com.luvin.survey.definition.SurveyDefinitionProfile;
import com.luvin.survey.definition.SurveyDefinitionProfileFeature;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Python classifier.py classify()의 분류 절반(유형 거리·tie-break·mixed/poorFit·근거 선정)을 이식한 순수 계산기.
 * 표시 반올림(6자리) 이전의 거리로 gap/tie/poor_fit을 판정하고, 판정용 반올림(6자리 HALF_UP)은 순위 결정에만 쓴다.
 */
public final class BreadTypeClassifier {

    private static final Rational CENTER = Rational.of(101, 2);
    private static final int RANK_SCALE = 6;

    private BreadTypeClassifier() {
    }

    public static BreadTypeClassificationResult classify(SurveyDefinitionConfig config, SurveyScoringResult scoring) {
        Map<String, Rational> exactScores = scoring.exactScores();

        Map<String, BigDecimal> distanceByProfile = new LinkedHashMap<>();
        Map<String, BigDecimal> rankKeyByProfile = new LinkedHashMap<>();
        for (SurveyDefinitionProfile profile : config.profiles()) {
            Rational numerator = Rational.ZERO;
            long denominator = 0;
            for (Map.Entry<String, SurveyDefinitionProfileFeature> entry : profile.features().entrySet()) {
                SurveyDefinitionProfileFeature feature = entry.getValue();
                Rational score = exactScores.get(entry.getKey());
                Rational low = Rational.of(feature.low());
                Rational high = Rational.of(feature.high());
                Rational d = Rational.max(Rational.max(low.subtract(score), Rational.ZERO), score.subtract(high));
                numerator = numerator.add(d.multiply(d).multiply(feature.weight()));
                denominator += feature.weight();
            }
            Rational square = numerator.divide(Rational.of(denominator));
            BigDecimal distance = square.toBigDecimal().sqrt(new MathContext(40));
            distanceByProfile.put(profile.id(), distance);
            rankKeyByProfile.put(profile.id(), distance.setScale(RANK_SCALE, RoundingMode.HALF_UP));
        }

        Map<String, Integer> priority = new LinkedHashMap<>();
        for (int i = 0; i < config.tieBreakOrder().size(); i++) {
            priority.put(config.tieBreakOrder().get(i), i);
        }
        List<String> ordered = new ArrayList<>(distanceByProfile.keySet());
        ordered.sort(Comparator
                .comparing((String key) -> rankKeyByProfile.get(key))
                .thenComparing(priority::get));

        String first = ordered.get(0);
        String second = ordered.get(1);
        BigDecimal gap = distanceByProfile.get(second).subtract(distanceByProfile.get(first))
                .max(BigDecimal.ZERO);
        boolean tied = rankKeyByProfile.get(first).compareTo(rankKeyByProfile.get(second)) == 0;
        boolean mixed = gap.compareTo(BigDecimal.valueOf(config.distanceGapThreshold())) < 0;
        boolean poorFit = distanceByProfile.get(first).compareTo(BigDecimal.valueOf(config.poorFitDistanceThreshold())) > 0;

        Map<String, BigDecimal> displayDistances = new LinkedHashMap<>();
        for (String key : ordered) {
            displayDistances.put(key, distanceByProfile.get(key).setScale(RANK_SCALE, RoundingMode.HALF_UP));
        }

        SurveyDefinitionProfile winner = config.profiles().stream()
                .filter(p -> p.id().equals(first)).findFirst().orElseThrow();
        List<ReasonEvidence> reasonEvidence = buildReasonEvidence(winner, exactScores, scoring.observedByDimension());

        return new BreadTypeClassificationResult(
                first, second, mixed, poorFit, tied,
                gap.setScale(RANK_SCALE, RoundingMode.HALF_UP),
                displayDistances,
                reasonEvidence);
    }

    private record ScoredReason(Rational sortKey, String dimension, int direction, List<String> sourceAnswerIds) {
    }

    private static List<ReasonEvidence> buildReasonEvidence(SurveyDefinitionProfile winner,
                                                              Map<String, Rational> exactScores,
                                                              Map<String, List<ObservedEvidence>> observedByDimension) {
        List<ScoredReason> reasons = new ArrayList<>();
        for (Map.Entry<String, SurveyDefinitionProfileFeature> entry : winner.features().entrySet()) {
            String dimension = entry.getKey();
            SurveyDefinitionProfileFeature feature = entry.getValue();
            int direction = directionOf(feature);
            Rational aligned = exactScores.get(dimension).subtract(CENTER).multiply(direction);
            if (aligned.compareTo(Rational.of(5)) <= 0) {
                continue;
            }
            List<String> sourceIds = observedByDimension.get(dimension).stream()
                    .filter(e -> e.value() == direction)
                    .map(ObservedEvidence::answerId)
                    .toList();
            if (sourceIds.isEmpty()) {
                continue;
            }
            reasons.add(new ScoredReason(aligned.multiply(feature.weight()), dimension, direction, sourceIds));
        }
        reasons.sort(Comparator.comparing((ScoredReason r) -> r.sortKey(), Comparator.reverseOrder())
                .thenComparing(ScoredReason::dimension));
        return reasons.stream()
                .limit(3)
                .map(r -> new ReasonEvidence(r.dimension(), r.direction(), r.sourceAnswerIds()))
                .toList();
    }

    private static int directionOf(SurveyDefinitionProfileFeature feature) {
        // center=50.5, low/high는 정수이므로 low>center <=> low>=51, high<center <=> high<=50.
        if (feature.low() >= 51) {
            return 1;
        }
        if (feature.high() <= 50) {
            return -1;
        }
        return 0;
    }
}
