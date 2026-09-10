package test.luvin_backend_v2.analysis.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalysisChatRequest(
        @NotBlank(message = "message는 필수입니다.")
        String message
) {
}