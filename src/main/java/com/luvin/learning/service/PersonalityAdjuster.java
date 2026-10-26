package com.luvin.learning.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 성격 신호(축별 변화량)를 누적 보정에 더한다. 한 번에 축별 ±{@link LearningPolicy#PERSONALITY_PER_RUN_CAP},
 * 누적 ±{@link LearningPolicy#PERSONALITY_CUMULATIVE_CAP}까지만 움직여 설문 결과(기본값)를 뒤집지 않는다.
 */
public final class PersonalityAdjuster {

    private PersonalityAdjuster() {
    }

    public static Map<String, Double> apply(Map<String, Double> cumulative, Map<String, Double> deltas) {
        Map<String, Double> next = new LinkedHashMap<>();
        for (String axis : LearningPolicy.PERSONALITY_AXES) {
            double current = cumulative == null ? 0.0 : cumulative.getOrDefault(axis, 0.0);
            Double delta = deltas == null ? null : deltas.get(axis);
            double step = delta == null || !Double.isFinite(delta) ? 0.0
                    : clamp(delta, LearningPolicy.PERSONALITY_PER_RUN_CAP);
            double value = round(clamp(current + step, LearningPolicy.PERSONALITY_CUMULATIVE_CAP));
            if (value != 0.0) {
                next.put(axis, value);
            }
        }
        return next;
    }

    /** 설문 점수에 보정을 더한 점수(1~100). */
    public static int adjustedScore(int surveyScore, Map<String, Double> cumulative, String axis) {
        double delta = cumulative == null ? 0.0 : cumulative.getOrDefault(axis, 0.0);
        return (int) Math.round(Math.max(1, Math.min(100, surveyScore + delta)));
    }

    private static double clamp(double value, double cap) {
        return Math.max(-cap, Math.min(cap, value));
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
