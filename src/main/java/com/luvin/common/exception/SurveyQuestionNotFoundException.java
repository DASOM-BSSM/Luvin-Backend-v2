package com.luvin.common.exception;

public class SurveyQuestionNotFoundException extends RuntimeException {
  public SurveyQuestionNotFoundException(Long questionId) {
    super("설문 문항을 찾을 수 없습니다. questionId=" + questionId);
  }
}
