package com.luvin.survey.scoring;

/** 한 지표에 대해 실제로 선택된 답변이 남긴 근거 한 건. */
record ObservedEvidence(String questionId, String answerId, int value, int weight) {
}
