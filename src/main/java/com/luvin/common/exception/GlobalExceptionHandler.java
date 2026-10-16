package com.luvin.common.exception;

import com.luvin.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler({InsufficientTokenException.class, InvalidFeedbackException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler({InvalidGoogleTokenException.class, InvalidAppleTokenException.class, UnauthenticatedException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnauthenticated(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler({DuplicateAnswerException.class, DuplicateVoteException.class})
    public ResponseEntity<ApiResponse<Void>> handleDuplicateAnswer(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler({
            UserNotFoundException.class,
            MinigameNotFoundException.class,
            SurveyNotFoundException.class,
            SurveyQuestionNotFoundException.class,
            SurveyOptionNotFoundException.class,
            DailyQuestionNotFoundException.class,
            DailyQuestionOptionNotFoundException.class,
            EpisodeNotFoundException.class,
            ParticipantNotFoundException.class,
            GameNotFoundException.class,
            MatchNotFoundException.class,
            RebakeNotFoundException.class,
            ReportNotFoundException.class,
            MessageNotFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        // NoResourceFoundException(매핑 안 된 경로, 404), HttpRequestMethodNotSupportedException(405) 등
        // Spring MVC가 자체 상태 코드를 들고 있는 예외는 여기서 전부 500으로 뭉개지 않고 그 상태를 그대로 쓴다.
        // (고치기 전에는 이 catch-all이 먼저 잡아버려 매핑 안 된 경로조차 500으로 나갔다.)
        if (e instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(ApiResponse.error(e.getMessage()));
        }

        // 그 외 진짜 처리되지 않은 예외만 500이고, 운영에서 원인을 추적할 수 있도록 스택트레이스를 남긴다.
        log.error("처리되지 않은 예외가 발생했습니다.", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다."));
    }
}
