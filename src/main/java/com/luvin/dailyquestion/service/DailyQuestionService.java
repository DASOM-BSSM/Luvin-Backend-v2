package com.luvin.dailyquestion.service;

import com.luvin.dailyquestion.dto.DailyQuestionTodayResponse;

public interface DailyQuestionService {
    DailyQuestionTodayResponse getTodayQuestion(Long memberId);
    void submitAnswer(Long memberId, Long questionId, Long selectedOptionId);
}