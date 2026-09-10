package test.luvin_backend_v2.dailyquestion.service;

import com.luvin_backend_v2.dailyquestion.dto.DailyQuestionHistoryResponse;
import com.luvin_backend_v2.dailyquestion.dto.DailyQuestionTodayResponse;
import java.util.List;

public interface DailyQuestionService {
    DailyQuestionTodayResponse getTodayQuestion(Long memberId);
    void submitAnswer(Long memberId, Long questionId, Long selectedOptionId);
    List<DailyQuestionHistoryResponse> getHistory(Long memberId);
}