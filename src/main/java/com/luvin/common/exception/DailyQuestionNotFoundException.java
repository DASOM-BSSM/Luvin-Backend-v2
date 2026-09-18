package com.luvin.common.exception;

public class DailyQuestionNotFoundException extends RuntimeException {

    public DailyQuestionNotFoundException(Long questionId) {
        super(questionId == null
                ? "오늘의 질문을 찾을 수 없습니다."
                : "오늘의 질문을 찾을 수 없습니다. id=" + questionId);
    }
}
