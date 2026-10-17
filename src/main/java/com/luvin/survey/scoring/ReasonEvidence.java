package com.luvin.survey.scoring;

import java.util.List;

/** 대표 유형 판정에 실제로 부합한 근거 하나. direction은 그 유형의 대표 방향(+1/-1)이다. */
public record ReasonEvidence(String dimension, int direction, List<String> sourceAnswerIds) {
}
