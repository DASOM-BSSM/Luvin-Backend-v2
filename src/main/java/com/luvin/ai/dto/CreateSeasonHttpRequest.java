package com.luvin.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateSeasonHttpRequest(
        @NotNull @Valid AiCharacterProfileRequest representative
) {
}
