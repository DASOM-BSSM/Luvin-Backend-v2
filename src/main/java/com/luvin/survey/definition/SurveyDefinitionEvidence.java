package com.luvin.survey.definition;

/** 답변 하나가 특정 지표에 남기는 근거. value는 -1/0/1, weight는 1 이상의 정수. */
public record SurveyDefinitionEvidence(int value, int weight) {
}
