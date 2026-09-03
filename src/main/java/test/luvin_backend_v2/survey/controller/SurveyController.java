package com.luvin.survey;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.survey.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/surveys")
public class SurveyController {

    private final SurveyService surveyService;

    public SurveyController(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    @GetMapping
    public List<SurveyListItemResponse> getSurveys() {
        return surveyService.getSurveys();
    }

    @GetMapping("/{surveyId}")
    public SurveyDetailResponse getSurveyDetail(@PathVariable Long surveyId) {
        return surveyService.getSurveyDetail(surveyId);
    }

    @GetMapping("/{surveyId}/option")
    public SurveyOptionResponse getSurveyOptions(@PathVariable Long surveyId) {
        return surveyService.getSurveyOptions(surveyId);
    }

    @PostMapping("/{surveyId}/submit")
    public MessageResponse submit(@PathVariable Long surveyId,
                                  @RequestBody SurveySubmitRequest body) {
        Long memberId = SecurityUtils.getCurrentUserId();
        surveyService.submit(memberId, surveyId, body);
        return new MessageResponse("설문 제출 완료");
    }
}
