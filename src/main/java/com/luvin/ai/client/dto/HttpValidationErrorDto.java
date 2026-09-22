package com.luvin.ai.client.dto;

import java.util.List;

/**
 * AI 서비스(FastAPI)의 표준 422 응답 형태. 그 외 오류는 detail이 문자열이거나 다른 형태일 수 있어
 * {@link com.luvin.ai.client.AiErrorBodyParser}에서 두 형식을 모두 안전하게 수용한다 (요구사항 6절).
 */
public record HttpValidationErrorDto(
        List<ValidationErrorDto> detail
) {
    public record ValidationErrorDto(
            List<Object> loc,
            String msg,
            String type
    ) {
    }
}
