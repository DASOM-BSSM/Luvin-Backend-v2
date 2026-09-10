package test.luvin_backend_v2.common.exception;

public class SurveyQuestionNotFoundException extends RuntimeException {
  public SurveyQuestionNotFoundException(Long questionId) {
    super("설문 문항을 찾을 수 없습니다. questionId=" + questionId);
  }
}
