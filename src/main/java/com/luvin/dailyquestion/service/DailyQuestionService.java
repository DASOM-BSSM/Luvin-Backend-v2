package com.luvin.dailyquestion.service;

import com.luvin.dailyquestion.dto.DailyQuestionHistoryResponse;
import com.luvin.dailyquestion.dto.DailyQuestionTodayResponse;
import java.util.List;

public interface DailyQuestionService {
    DailyQuestionTodayResponse getTodayQuestion(Long memberId);
    void submitAnswer(Long memberId, Long questionId, Long selectedOptionId);
    List<DailyQuestionHistoryResponse> getHistory(Long memberId);
}