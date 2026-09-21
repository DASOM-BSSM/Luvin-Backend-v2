package com.luvin.survey.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.survey.dto.SurveyOptionResponse;
import com.luvin.survey.dto.SurveySubmitRequest;
import com.luvin.survey.service.SurveyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/surveys")
public class SurveyController {

    private final SurveyService surveyService;

    public SurveyController(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    @GetMapping("/{questionId}")
    public SurveyOptionResponse getQuestion(@PathVariable Long questionId) {
        return surveyService.getQuestion(questionId);
    }

    @PostMapping("/submit")
    public MessageResponse submit(@RequestBody SurveySubmitRequest body) {
        Long memberId = SecurityUtils.getCurrentUserId();
        surveyService.submit(memberId, body);
        return new MessageResponse("설문 제출 완료");
    }
}
