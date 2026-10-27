package com.luvin.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문서용: 오류 응답 본문(ApiResponse 형식). 실제로는 ApiResponse.error(...)가 내려간다.
 * code는 CodedApiException으로 던진 오류만 채워지고, BusinessException·도메인 NotFound 예외·검증 오류는 null이다.
 */
@Schema(name = "ApiErrorResponse", description = "오류 응답. success는 항상 false")
public record ApiErrorView(
        @Schema(example = "false") boolean success,
        @Schema(description = "대부분 null. 일부 오류만 추가 정보를 담는다", nullable = true) Object data,
        String message,
        @Schema(description = "machine code. 오류마다 다르며 code가 없는 오류는 null", nullable = true) String code
) {
}
