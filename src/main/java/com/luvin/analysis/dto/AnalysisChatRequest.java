package com.luvin.analysis.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalysisChatRequest(
        @NotBlank(message = "message는 필수입니다.")
        String message
) {
}