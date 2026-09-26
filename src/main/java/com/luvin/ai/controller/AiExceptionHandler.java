package com.luvin.ai.controller;

import com.luvin.ai.client.exception.AiAuthConfigException;
import com.luvin.ai.client.exception.AiContractViolationException;
import com.luvin.ai.client.exception.AiNotFoundException;
import com.luvin.ai.client.exception.AiRateLimitException;
import com.luvin.ai.client.exception.AiRerollLockedException;
import com.luvin.ai.client.exception.AiRevisionConflictException;
import com.luvin.ai.client.exception.AiSelectionConflictException;
import com.luvin.ai.client.exception.AiServerException;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.client.exception.AiValidationException;
import com.luvin.ai.client.exception.AiVersionSupersededException;
import com.luvin.ai.service.exception.AiCandidateInvalidException;
import com.luvin.ai.service.exception.AiEpisodeProgressionException;
import com.luvin.ai.service.exception.AiInputValidationException;
import com.luvin.ai.service.exception.AiReportNotReadyException;
import com.luvin.ai.service.exception.AiRerollNotAllowedException;
import com.luvin.ai.service.exception.AiRerollNotFoundException;
import com.luvin.ai.service.exception.AiSeasonAlreadyExistsException;
import com.luvin.ai.service.exception.AiSeasonNotFoundException;
import com.luvin.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * AI 서비스 연동에서 발생하는 예외를 요구사항 6절의 분류에 따라 앱 응답으로 변환한다.
 * 어떤 경우에도 AI 내부 오류, provider 응답 원문, prompt, stack trace를 그대로 노출하지 않는다 (요구사항 7절).
 */
@RestControllerAdvice
@Order(0)
public class AiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiExceptionHandler.class);

    @ExceptionHandler(AiSeasonNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSeasonNotFound(AiSeasonNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AiRerollNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRerollNotFound(AiRerollNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AiNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiNotFound(AiNotFoundException e) {
        // 다른 사용자 자원의 존재 여부를 노출하지 않기 위해 일반적인 메시지만 반환한다 (요구사항 6, 7절).
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("요청한 자원을 찾을 수 없습니다."));
    }

    @ExceptionHandler({
            AiRevisionConflictException.class,
            AiSelectionConflictException.class,
            AiEpisodeProgressionException.class,
            AiSeasonAlreadyExistsException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler({AiRerollLockedException.class, AiRerollNotAllowedException.class})
    public ResponseEntity<ApiResponse<Void>> handleRerollLocked(RuntimeException e) {
        // REROLL_LOCKED는 정상적인 도메인 충돌이다 (요구사항 5.5).
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("지금은 reroll할 수 없습니다."));
    }

    @ExceptionHandler(AiVersionSupersededException.class)
    public ResponseEntity<ApiResponse<Void>> handleVersionSuperseded(AiVersionSupersededException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("내용이 갱신되었습니다. 최신 버전을 다시 불러와 주세요."));
    }

    @ExceptionHandler({AiInputValidationException.class, AiCandidateInvalidException.class})
    public ResponseEntity<ApiResponse<Void>> handleInputValidation(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AiValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiValidation(AiValidationException e) {
        // Spring이 사전 검증을 통과시켰는데도 AI가 422를 반환했다면 계약 불일치(버그)다. 재시도하지 않는다.
        log.error("AI service returned 422 despite passing Spring-side validation — possible contract drift");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(AiRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(AiRateLimitException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("요청이 많아 처리가 지연되고 있습니다. 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler({AiServerException.class, AiAuthConfigException.class})
    public ResponseEntity<ApiResponse<Void>> handleServerOrAuthConfig(AiServiceException e) {
        // 401은 내부 인증 구성 오류로 기록하고, 절대 "재로그인 필요"로 변환하지 않는다 (요구사항 6절).
        log.error("AI service call failed with server/auth-config error: {}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("일시적인 오류로 요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(AiContractViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleContractViolation(AiContractViolationException e) {
        log.error("AI service contract violation detected: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("일시적인 오류로 요청을 처리하지 못했습니다."));
    }

    @ExceptionHandler(AiReportNotReadyException.class)
    public ResponseEntity<ApiResponse<Void>> handleReportNotReady(AiReportNotReadyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericAiServiceException(AiServiceException e) {
        log.error("Unclassified AI service exception: {}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error("AI 서비스 연동 중 오류가 발생했습니다."));
    }
}
