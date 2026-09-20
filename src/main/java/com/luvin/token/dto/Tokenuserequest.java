package com.luvin.token.dto;

import com.luvin.token.domain.TokenUsageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TokenUseRequest(
        @Positive(message = "amount는 1 이상이어야 합니다.")
        int amount,
        @NotNull(message = "usageType은 필수입니다.")
        TokenUsageType usageType
) {
}