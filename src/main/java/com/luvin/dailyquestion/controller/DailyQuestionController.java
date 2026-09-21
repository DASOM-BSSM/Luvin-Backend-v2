package com.luvin.dailyquestion.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.dailyquestion.dto.DailyQuestionAnswerRequest;
import com.luvin.dailyquestion.dto.DailyQuestionTodayResponse;
import com.luvin.dailyquestion.service.DailyQuestionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily_questions")
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
}
