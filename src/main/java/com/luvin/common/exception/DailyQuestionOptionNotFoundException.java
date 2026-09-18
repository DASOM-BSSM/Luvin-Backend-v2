package com.luvin.common.exception;

public class DailyQuestionOptionNotFoundException extends RuntimeException {

    public DailyQuestionOptionNotFoundException(Long optionId) {
        super("오늘의 질문 선택지를 찾을 수 없습니다. id=" + optionId);
    }
}

