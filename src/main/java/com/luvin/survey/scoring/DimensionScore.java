package com.luvin.survey.scoring;

import java.math.BigDecimal;

/** 지표 하나의 표시용 점수. score는 소수 둘째 자리 HALF_UP. */
public record DimensionScore(BigDecimal score, int evidenceWeight, int observations, boolean directionConflict) {
}
