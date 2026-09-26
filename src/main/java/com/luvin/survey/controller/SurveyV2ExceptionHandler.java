package com.luvin.survey.controller;

import com.luvin.common.response.ApiResponse;
import com.luvin.survey.scoring.SurveyAnswerValidationException;
import com.luvin.survey.service.exception.ProfileRequiredException;
import com.luvin.survey.service.exception.SurveyDefinitionUnavailableException;
import com.luvin.survey.service.exception.SurveyIdempotencyConflictException;
import com.luvin.survey.service.exception.SurveyRequiredException;
import com.luvin.survey.service.exception.SurveyResultNotFoundException;
import com.luvin.survey.service.exception.SurveyResultStaleException;
import com.luvin.survey.service.exception.SurveySubmissionNotFoundException;
import com.luvin.survey.service.exception.SurveyVersionRetiredException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * SHARED_API_CONTRACT.md 8절의 오류 계약을 그대로 반영한다. malformed JSON 등 parser 오류도 500이
 * 아니라 400으로 변환한다 — 기존 GlobalExceptionHandler의 광범위 Exception→500 catch-all보다
 * 먼저 잡히도록 @Order(0)으로 우선순위를 준다(AiExceptionHandler와 동일한 패턴).
 */
@RestControllerAdvice
@Order(0)
public class SurveyV2ExceptionHandler {

    @ExceptionHandler(SurveyAnswerValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAnswerValidation(SurveyAnswerValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage(), e.getCode()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("요청 본문을 읽을 수 없습니다.", "SURVEY_REQUEST_INVALID"));
    }

    @ExceptionHandler(SurveySubmissionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSubmissionNotFound(SurveySubmissionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage(), "SURVEY_SUBMISSION_NOT_FOUND"));
    }

    @ExceptionHandler(SurveyResultNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResultNotFound(SurveyResultNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage(), "SURVEY_RESULT_NOT_FOUND"));
    }

    @ExceptionHandler(SurveyIdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(SurveyIdempotencyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage(), "SURVEY_IDEMPOTENCY_CONFLICT"));
    }

    @ExceptionHandler(SurveyRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleSurveyRequired(SurveyRequiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage(), "SURVEY_REQUIRED"));
    }

    @ExceptionHandler(SurveyResultStaleException.class)
    public ResponseEntity<ApiResponse<Void>> handleResultStale(SurveyResultStaleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage(), "SURVEY_RESULT_STALE"));
    }

    @ExceptionHandler(ProfileRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleProfileRequired(ProfileRequiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage(), "PROFILE_REQUIRED"));
    }

    @ExceptionHandler(SurveyVersionRetiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleVersionRetired(SurveyVersionRetiredException e) {
        return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.error(e.getMessage(), "SURVEY_VERSION_RETIRED"));
    }

    @ExceptionHandler(SurveyDefinitionUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDefinitionUnavailable(SurveyDefinitionUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(e.getMessage(), "SURVEY_DEFINITION_UNAVAILABLE"));
    }
}
