package com.luvin.survey.controller;

import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.survey.dto.SurveyDefinitionResponse;
import com.luvin.survey.dto.SurveyResultResponse;
import com.luvin.survey.dto.SurveyV2SubmitRequest;
import com.luvin.survey.service.SurveyDefinitionService;
import com.luvin.survey.service.SurveySubmissionOutcome;
import com.luvin.survey.service.SurveySubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * SHARED_API_CONTRACT.md의 신규 설문 v2 API. 기존 /api/surveys/{questionId}, /api/surveys/submit
 * (SurveyController)은 그대로 두고 건드리지 않는다 — 이 controller가 완전히 새 경로를 쓴다.
 */
@RestController
@RequestMapping("/api/surveys/v2")
@RequiredArgsConstructor
public class SurveyV2Controller {

    private final SurveyDefinitionService surveyDefinitionService;
    private final SurveySubmissionService surveySubmissionService;

    @GetMapping("/definition")
    public ApiResponse<SurveyDefinitionResponse> getDefinition() {
        return ApiResponse.ok(surveyDefinitionService.getCurrentDefinition());
    }

    @PostMapping("/submissions")
    public ResponseEntity<ApiResponse<SurveyResultResponse>> submit(@Valid @RequestBody SurveyV2SubmitRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        SurveySubmissionOutcome outcome = surveySubmissionService.submit(memberId, request);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(outcome.result()));
    }

    @GetMapping("/results/me")
    public ApiResponse<SurveyResultResponse> getMyResult() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(surveySubmissionService.getLatestResult(memberId).orElse(null));
    }

    @GetMapping("/submissions/{clientSubmissionId}")
    public ApiResponse<SurveyResultResponse> getSubmission(@PathVariable UUID clientSubmissionId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(surveySubmissionService.getByClientSubmissionId(memberId, clientSubmissionId));
    }
}
