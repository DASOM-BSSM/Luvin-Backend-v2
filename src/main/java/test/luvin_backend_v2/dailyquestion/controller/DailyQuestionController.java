package test.luvin_backend_v2.dailyquestion.controller;

import com.luvin_backend_v2.common.response.MessageResponse;
import com.luvin_backend_v2.common.security.SecurityUtils;
import com.luvin_backend_v2.dailyquestion.dto.DailyQuestionAnswerRequest;
import com.luvin_backend_v2.dailyquestion.dto.DailyQuestionHistoryResponse;
import com.luvin_backend_v2.dailyquestion.dto.DailyQuestionTodayResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/daily-questions")
public class DailyQuestionController {

    private final DailyQuestionService dailyQuestionService;

    public DailyQuestionController(DailyQuestionService dailyQuestionService) {
        this.dailyQuestionService = dailyQuestionService;
    }

    @GetMapping("/today")
    public DailyQuestionTodayResponse getTodayQuestion() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return dailyQuestionService.getTodayQuestion(memberId);
    }

    @PostMapping("/{questionId}/answer")
    public MessageResponse answer(@PathVariable Long questionId,
                                  @RequestBody DailyQuestionAnswerRequest body) {
        Long memberId = SecurityUtils.getCurrentUserId();
        dailyQuestionService.submitAnswer(memberId, questionId, body.getSelectedOption());
        return new MessageResponse("응답 저장 완료");
    }

    @GetMapping("/history")
    public List<DailyQuestionHistoryResponse> getHistory() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return dailyQuestionService.getHistory(memberId);
    }
}
