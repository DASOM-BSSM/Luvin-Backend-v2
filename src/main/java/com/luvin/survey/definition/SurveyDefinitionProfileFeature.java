package com.luvin.survey.definition;

import java.util.List;

/** range=[low,high] 대표 범위, weight는 유형 거리 계산 시 이 지표의 중요도. */
public record SurveyDefinitionProfileFeature(List<Integer> range, int weight) {
    public int low() {
        return range.get(0);
    }

    public int high() {
        return range.get(1);
    }
}
